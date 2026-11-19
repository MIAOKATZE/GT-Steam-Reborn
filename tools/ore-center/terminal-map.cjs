// SPDX-License-Identifier: AGPL-3.0-only
// Bake the same assembly miniature and pick anchors used by the approved design.
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { deflateSync, crc32 } = require("node:zlib");

const root = path.resolve(__dirname, "../..");
const preview = path.join(root, "plan", "矿物加工集成中心工程");
const output = path.join(root, "src/main/resources/assets/gtsr/textures/gui/orecenter/map");
global.window = global;
global.ImageData = class {
    constructor(width, height) {
        this.width = width;
        this.height = height;
        this.data = new Uint8ClampedArray(width * height * 4);
    }
};
for (const name of ["block-palette", "factory-base", "factory-modules", "voxel-model", "terminal-map"]) {
    let source = fs.readFileSync(path.join(preview, name + ".js"), "utf8");
    if (name === "terminal-map") {
        source = source.replace("RESOLUTION = 2", "RESOLUTION = 1");
        source = source.replace("  window.createOreTerminalMap = ", "  window.renderOreMiniature = render;\n  window.createOreTerminalMap = ");
    }
    vm.runInThisContext(source, { filename: name });
}

function chunk(name, bytes) {
    const type = Buffer.from(name);
    const frame = Buffer.alloc(12 + bytes.length);
    frame.writeUInt32BE(bytes.length);
    type.copy(frame, 4);
    bytes.copy(frame, 8);
    frame.writeUInt32BE(crc32(frame.subarray(4, 8 + bytes.length)), 8 + bytes.length);
    return frame;
}

function png(image) {
    const { width, height, data } = image;
    const header = Buffer.alloc(13);
    header.writeUInt32BE(width);
    header.writeUInt32BE(height, 4);
    header[8] = 8;
    header[9] = 6;
    const pixels = Buffer.from(data.buffer), rowWidth = width * 4;
    const scanlines = Buffer.alloc(height * (rowWidth + 1));
    for (let y = 0; y < height; y++) {
        const line = y * (rowWidth + 1);
        scanlines[line] = 1;
        for (let x = 0; x < rowWidth; x++) {
            const i = y * rowWidth + x;
            scanlines[line + 1 + x] = pixels[i] - (x >= 4 ? pixels[i - 4] : 0);
        }
    }
    return Buffer.concat([
        Buffer.from("89504e470d0a1a0a", "hex"),
        chunk("IHDR", header),
        chunk("IDAT", deflateSync(scanlines, { level: 9 })),
        chunk("IEND", Buffer.alloc(0)),
    ]);
}

fs.mkdirSync(output, { recursive: true });
const anchors = Array.from({length: 4}, () => Buffer.alloc(64 * 36 * 4));
for (let mask = 0; mask <= 63; mask++) {
    let base;
    for (let tier = 0; tier < 4; tier++) {
        const drawOuter = global.buildOreFactoryOuter;
        if (mask === 0) global.buildOreFactoryOuter = () => {};
        const model = makeOreVoxelModel(tier, [], mask || 1);
        global.buildOreFactoryOuter = drawOuter;
        const { image, positions } = renderOreMiniature(model);
        if (tier === 0) base = image.data;
        else {
            for (let i = 0; i < base.length; i += 4) {
                if (base[i + 3] !== image.data[i + 3])
                    throw Error("Tower tiers must share the same solid silhouette");
                if (base[i] === image.data[i] && base[i + 1] === image.data[i + 1] && base[i + 2] === image.data[i + 2])
                    image.data.fill(0, i, i + 4);
            }
        }
        fs.writeFileSync(path.join(output, `${tier}-${mask}.png`), png(image));
        positions.forEach(([x, y], slot) => {
            const offset = (mask * 36 + slot) * 4;
            anchors[tier].writeInt16BE(Math.round(x), offset);
            anchors[tier].writeInt16BE(Math.round(y), offset + 2);
        });
    }
    if (mask % 16 === 15) console.log(`Miniature ${mask + 1}/64 configurations`);
}
anchors.forEach((data, tier) => fs.writeFileSync(path.join(output, `${tier}.anchors`), data));
