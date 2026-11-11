-- Aseprite Pixel-Art Generator for Changed: Synergy Native Origin Archive UI
-- Generates all 22 asset keys from ASSET_CHECKLIST.csv as multi-layer .aseprite files
-- and exports crisp pixel-art .png textures into the mod resource directory.

local pngDir = app.params["png_dir"] .. "/"
local aseDir = app.params["source_dir"] .. "/"

app.fs.makeAllDirectories(pngDir)
app.fs.makeAllDirectories(aseDir)

-- Deterministic PRNG for reproducible pixel-art grain
local rngState = 1337421
local function randInt(n)
    rngState = (rngState * 1103515245 + 12345) % 2147483648
    return (rngState % n)
end

local function rgba(r, g, b, a)
    return app.pixelColor.rgba(r, g, b, a or 255)
end

local function fillRect(img, x0, y0, w, h, col)
    local x1 = math.min(img.width - 1, x0 + w - 1)
    local y1 = math.min(img.height - 1, y0 + h - 1)
    x0 = math.max(0, x0)
    y0 = math.max(0, y0)
    for y = y0, y1 do
        for x = x0, x1 do
            img:drawPixel(x, y, col)
        end
    end
end

local function drawBlock(img, bx, by, scale, col)
    fillRect(img, bx * scale, by * scale, scale, scale, col)
end

local function fillBlockRect(img, bx, by, bw, bh, scale, col)
    fillRect(img, bx * scale, by * scale, bw * scale, bh * scale, col)
end

local function strokeBlockRect(img, bx, by, bw, bh, scale, col)
    fillBlockRect(img, bx, by, bw, 1, scale, col)
    fillBlockRect(img, bx, by + bh - 1, bw, 1, scale, col)
    fillBlockRect(img, bx, by, 1, bh, scale, col)
    fillBlockRect(img, bx + bw - 1, by, 1, bh, scale, col)
end

-- 4x4 Bayer matrix (0..15)
local bayer4 = {
    { 0,  8,  2, 10},
    {12,  4, 14,  6},
    { 3, 11,  1,  9},
    {15,  7, 13,  5}
}

local function bayerVal(bx, by)
    return bayer4[(by % 4) + 1][(bx % 4) + 1]
end

local function addLayerWithImage(spr, name)
    local layer = spr:newLayer()
    layer.name = name
    local cel = spr:newCel(layer, 1)
    return layer, cel.image
end

local function saveAseAndPng(spr, name, exportPngPath)
    spr:saveAs(aseDir .. name .. ".aseprite")
    if exportPngPath then
        spr:saveCopyAs(exportPngPath)
    else
        spr:saveCopyAs(pngDir .. name .. ".png")
    end
end

local function buildFolderAssets()
    local S = 3
    -- folder_front logical bounds: x = 0..842 (281 blocks), y = -46..1195 (414 blocks: top 7 blocks are ear y=-46..-26, body is 407 blocks y=-26..1195)
    local BW, BH = 281, 414
    local W, H = BW * S, BH * S

    local function makeFolderFront(name, isHover, isInner)
        local spr = Sprite(W, H, ColorMode.RGB)
        spr.layers[1].name = "folder_stock"
        local baseImg = spr:newCel(spr.layers[1], 1).image
        local _, grainImg = addLayerWithImage(spr, "kraft_grain")
        local _, borderImg = addLayerWithImage(spr, "border_and_spine")
        local _, stampImg = addLayerWithImage(spr, "cover_frame_emboss")

        local cStock = isInner and rgba(182, 154, 106, 255) or (isHover and rgba(208, 178, 124, 255) or rgba(196, 165, 110, 255))
        local cGrain1 = isInner and rgba(172, 144, 98, 255) or (isHover and rgba(216, 186, 132, 255) or rgba(204, 173, 118, 255))
        local cGrain2 = isInner and rgba(164, 136, 90, 255) or (isHover and rgba(196, 165, 110, 255) or rgba(184, 153, 100, 255))
        local cOutline = isHover and rgba(76, 56, 30, 255) or rgba(90, 68, 40, 255)
        local cHi = isHover and rgba(236, 210, 158, 255) or rgba(218, 190, 136, 255)
        local cShad = rgba(146, 118, 72, 255)
        local cSpine = isInner and rgba(148, 120, 76, 255) or rgba(156, 126, 80, 255)
        local cCrease = rgba(110, 84, 48, 255)
        local cBrass = isHover and rgba(226, 192, 108, 255) or rgba(188, 154, 82, 255)

        -- Top ear tab: x = 616..820 -> bx = 205..273, by = 0..6
        for by = 0, 6 do
            local inset = math.max(0, 3 - by)
            fillBlockRect(baseImg, 205 + inset, by, (273 - 205 + 1) - inset * 2, 1, S, cStock)
            drawBlock(borderImg, 205 + inset, by, S, cOutline)
            drawBlock(borderImg, 273 - inset, by, S, cOutline)
            if by == 0 then
                fillBlockRect(borderImg, 205 + inset, 0, (273 - 205 + 1) - inset * 2, 1, S, cOutline)
            elseif by == 1 then
                fillBlockRect(borderImg, 205 + inset + 1, 1, (273 - 205 + 1) - inset * 2 - 2, 1, S, cHi)
            end
        end

        -- Main cover body: by = 7..BH-1, bx = 0..BW-1
        fillBlockRect(baseImg, 0, 7, BW, BH - 7, S, cStock)

        rngState = isInner and 3103 or 3101
        for by = 9, BH - 3 do
            for bx = 2, BW - 3 do
                local r = randInt(100)
                if r < 14 then
                    drawBlock(grainImg, bx, by, S, cGrain1)
                elseif r < 26 then
                    drawBlock(grainImg, bx, by, S, cGrain2)
                end
            end
        end

        -- Spine strip on left (bx = 0..9)
        fillBlockRect(borderImg, 0, 7, 9, BH - 7, S, cSpine)
        fillBlockRect(borderImg, 3, 7, 2, BH - 7, S, cCrease)
        fillBlockRect(borderImg, 9, 7, 1, BH - 7, S, cShad)

        -- Outer pixel border & bevel
        strokeBlockRect(borderImg, 0, 7, BW, BH - 7, S, cOutline)
        -- Open up ear seam at by = 7, bx = 206..272
        fillBlockRect(borderImg, 206, 7, 67, 1, S, cStock)
        fillBlockRect(borderImg, 1, 8, 204, 1, S, cHi)
        fillBlockRect(borderImg, 274, 8, BW - 276, 1, S, cHi)
        fillBlockRect(borderImg, 1, BH - 2, BW - 2, 1, S, cShad)
        fillBlockRect(borderImg, BW - 2, 8, 1, BH - 9, S, cShad)

        -- Keep the cover corners plain; no metal corner plates.

        if not isInner then
            -- Embossed pixel frame around cover title area (leaves interior clean for live font rendering)
            strokeBlockRect(stampImg, 28, 60, BW - 52, 92, S, cOutline)
            strokeBlockRect(stampImg, 30, 62, BW - 56, 88, S, cShad)
            -- Corner notches
            for _, cx in ipairs({28, BW - 26}) do
                for _, cy in ipairs({60, 150}) do
                    fillBlockRect(stampImg, cx, cy, 2, 2, S, cBrass)
                end
            end
            -- Bottom classification rules
            fillBlockRect(stampImg, 28, BH - 46, BW - 52, 1, S, cShad)
            fillBlockRect(stampImg, 28, BH - 32, BW - 52, 1, S, cShad)
        else
            -- Inner folder lining rule lines
            strokeBlockRect(stampImg, 16, 20, BW - 28, BH - 34, S, cShad)
        end

        saveAseAndPng(spr, name, pngDir .. name .. ".png")
        spr:close()
    end

    makeFolderFront("folder_front", false, false)
    makeFolderFront("folder_front_hover", true, false)
    makeFolderFront("folder_inner", false, true)

    -- folder_back: logical x = -24..842 (866 -> 289 blocks), y = -46..1195 (1241 -> 414 blocks)
    local BBW, BBH = 289, 414
    local sprB = Sprite(BBW * S, BBH * S, ColorMode.RGB)
    sprB.layers[1].name = "rear_board"
    local bImg = sprB:newCel(sprB.layers[1], 1).image
    local _, bGrain = addLayerWithImage(sprB, "kraft_grain")
    local _, bBorder = addLayerWithImage(sprB, "border_bevel")

    local cbStock = rgba(178, 148, 94, 255)
    local cbG1 = rgba(186, 155, 100, 255)
    local cbG2 = rgba(168, 138, 86, 255)
    local cbOut = rgba(78, 58, 32, 255)
    local cbHi = rgba(202, 172, 116, 255)
    local cbSh = rgba(138, 110, 66, 255)

    -- Top spine ear: logical x = -2..136 (offset +24 -> 22..160 -> bx = 7..53), by = 0..6
    for by = 0, 6 do
        local inset = math.max(0, 3 - by)
        fillBlockRect(bImg, 7 + inset, by, (53 - 7 + 1) - inset * 2, 1, S, cbStock)
        drawBlock(bBorder, 7 + inset, by, S, cbOut)
        drawBlock(bBorder, 53 - inset, by, S, cbOut)
        if by == 0 then
            fillBlockRect(bBorder, 7 + inset, 0, (53 - 7 + 1) - inset * 2, 1, S, cbOut)
        elseif by == 1 then
            fillBlockRect(bBorder, 7 + inset + 1, 1, (53 - 7 + 1) - inset * 2 - 2, 1, S, cbHi)
        end
    end

    fillBlockRect(bImg, 0, 7, BBW, BBH - 7, S, cbStock)
    rngState = 3201
    for by = 9, BBH - 3 do
        for bx = 2, BBW - 3 do
            local r = randInt(100)
            if r < 14 then
                drawBlock(bGrain, bx, by, S, cbG1)
            elseif r < 26 then
                drawBlock(bGrain, bx, by, S, cbG2)
            end
        end
    end
    strokeBlockRect(bBorder, 0, 7, BBW, BBH - 7, S, cbOut)
    fillBlockRect(bBorder, 8, 7, 45, 1, S, cbStock)
    fillBlockRect(bBorder, 1, 8, BBW - 2, 1, S, cbHi)
    fillBlockRect(bBorder, 1, BBH - 2, BBW - 2, 1, S, cbSh)
    fillBlockRect(bBorder, BBW - 2, 8, 1, BBH - 9, S, cbSh)

    saveAseAndPng(sprB, "folder_back", pngDir .. "folder_back.png")
    sprB:close()
end

buildFolderAssets()
