"""Eski (Anvil, 1.2-1.12) Minecraft dunyalarini okumak icin kucuk yardimcilar: NBT ve bolge (.mca) dosyalari.

Sadece okuma yapar. tools/import_map.py tarafindan kullanilir.
"""
import gzip
import struct
import zlib
from pathlib import Path


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, n):
        chunk = self.data[self.pos:self.pos + n]
        self.pos += n
        return chunk

    def string(self):
        (length,) = struct.unpack(">H", self.take(2))
        return self.take(length).decode("utf-8", "replace")

    def payload(self, tag):
        if tag == 1:
            return struct.unpack(">b", self.take(1))[0]
        if tag == 2:
            return struct.unpack(">h", self.take(2))[0]
        if tag == 3:
            return struct.unpack(">i", self.take(4))[0]
        if tag == 4:
            return struct.unpack(">q", self.take(8))[0]
        if tag == 5:
            return struct.unpack(">f", self.take(4))[0]
        if tag == 6:
            return struct.unpack(">d", self.take(8))[0]
        if tag == 7:
            (n,) = struct.unpack(">i", self.take(4))
            return self.take(n)
        if tag == 8:
            return self.string()
        if tag == 9:
            item = self.take(1)[0]
            (n,) = struct.unpack(">i", self.take(4))
            return [self.payload(item) for _ in range(n)]
        if tag == 10:
            result = {}
            while True:
                child = self.take(1)[0]
                if child == 0:
                    return result
                name = self.string()
                result[name] = self.payload(child)
        if tag == 11:
            (n,) = struct.unpack(">i", self.take(4))
            return list(struct.unpack(f">{n}i", self.take(4 * n)))
        if tag == 12:
            (n,) = struct.unpack(">i", self.take(4))
            return list(struct.unpack(f">{n}q", self.take(8 * n)))
        raise ValueError(f"bilinmeyen NBT etiketi {tag}")


def read_nbt(data):
    reader = Reader(data)
    tag = reader.take(1)[0]
    reader.string()
    return reader.payload(tag)


def read_level_dat(path):
    return read_nbt(gzip.decompress(Path(path).read_bytes()))


def region_chunks(path):
    """Bolge dosyasindaki her parcanin NBT kokunu verir."""
    data = Path(path).read_bytes()
    for i in range(1024):
        offset = int.from_bytes(data[i * 4:i * 4 + 3], "big") * 4096
        if offset == 0:
            continue
        length = int.from_bytes(data[offset:offset + 4], "big")
        kind = data[offset + 4]
        raw = data[offset + 5:offset + 4 + length]
        if kind == 1:
            raw = gzip.decompress(raw)
        elif kind == 2:
            raw = zlib.decompress(raw)
        else:
            continue
        yield read_nbt(raw)


def legacy_blocks(world):
    """(x, y, z) -> (id, data) ve tile entity listesi (1.8 bicimi, 'Sections' + 'Blocks')."""
    blocks = {}
    tiles = []
    for region in sorted(Path(world, "region").glob("r.*.mca")):
        for root in region_chunks(region):
            level = root.get("Level", root)
            cx, cz = level["xPos"], level["zPos"]
            tiles.extend(level.get("TileEntities", []))
            for section in level.get("Sections", []):
                ids = section["Blocks"]
                add = section.get("Add")
                meta = section["Data"]
                base_y = section["Y"] * 16
                for i in range(4096):
                    block = ids[i]
                    if add is not None:
                        block |= ((add[i >> 1] >> ((i & 1) * 4)) & 0xF) << 8
                    if block == 0:
                        continue
                    value = (meta[i >> 1] >> ((i & 1) * 4)) & 0xF
                    x = cx * 16 + (i & 15)
                    z = cz * 16 + ((i >> 4) & 15)
                    y = base_y + (i >> 8)
                    blocks[(x, y, z)] = (block, value)
    return blocks, tiles
