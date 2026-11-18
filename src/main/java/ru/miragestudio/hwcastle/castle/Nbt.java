package ru.miragestudio.hwcastle.castle;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Минимальный читатель NBT (gzip), достаточный для Sponge-схематик (.schem).
 * Значения: byte/short/int/long/float/double -> соответствующие Number, строки -> String,
 * массивы -> byte[]/int[]/long[], списки -> List, compound -> Map.
 */
final class Nbt {

    private Nbt() {
    }

    static Map<String, Object> readRoot(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            byte type = in.readByte();
            if (type != 10) {
                throw new IOException("Корневой тег схематики не является compound");
            }
            in.readUTF(); // имя корня
            return readCompound(in);
        }
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == 0) {
                return map;
            }
            String name = in.readUTF();
            map.put(name, readValue(in, type));
        }
    }

    private static Object readValue(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                byte[] bytes = new byte[in.readInt()];
                in.readFully(bytes);
                return bytes;
            }
            case 8:
                return in.readUTF();
            case 9: {
                byte elementType = in.readByte();
                int size = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, size));
                for (int i = 0; i < size; i++) {
                    list.add(readValue(in, elementType));
                }
                return list;
            }
            case 10:
                return readCompound(in);
            case 11: {
                int[] ints = new int[in.readInt()];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = in.readInt();
                }
                return ints;
            }
            case 12: {
                long[] longs = new long[in.readInt()];
                for (int i = 0; i < longs.length; i++) {
                    longs[i] = in.readLong();
                }
                return longs;
            }
            default:
                throw new IOException("Неизвестный тип NBT-тега: " + type);
        }
    }
}
