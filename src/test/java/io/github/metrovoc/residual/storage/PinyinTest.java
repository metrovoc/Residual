package io.github.metrovoc.residual.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class PinyinTest {
    @Test
    void chineseNamesAreFoundByHowTheySound() {
        assertTrue(Pinyin.holds("铁矿石", "tiekuangshi"));
        assertTrue(Pinyin.holds("铁矿石", "tks"));
        assertTrue(Pinyin.holds("铁矿石", "tiek"));
        assertTrue(Pinyin.holds("铁矿石", "tkshi"));
        assertTrue(Pinyin.holds("铁矿石", "kuang"), "from any character on");
        assertFalse(Pinyin.holds("铁矿石", "kt"), "in the order of the name");
        assertFalse(Pinyin.holds("铁矿石", "tiedan"));
    }

    @Test
    void everyReadingOfACharacterCounts() {
        assertTrue(Pinyin.holds("重型压力板", "zhongxing"));
        assertTrue(Pinyin.holds("重型压力板", "chongxing"));
        assertTrue(Pinyin.holds("长剑", "changjian"));
        assertTrue(Pinyin.holds("长剑", "zhangjian"));
        assertTrue(Pinyin.holds("绿色染料", "lvse"), "v for the two-dotted u, as it is typed");
    }

    @Test
    void lettersStillMatchThemselves() {
        assertTrue(Pinyin.holds("Transformer 块", "transformer"));
        assertTrue(Pinyin.holds("Transformer 块", "former k"));
        assertTrue(Pinyin.holds("Iron Ingot", "iron"));
        assertFalse(Pinyin.holds("Iron Ingot", "tie"), "a name with no Chinese in it has no sound to go by");
        assertTrue(Pinyin.holds("铁锭", "铁"), "what is written is found as written");
    }

    @Test
    void aSearchFindsChineseNamesThroughIt() {
        StorageSearch.Facts iron = new StorageSearch.Facts() {
            @Override
            public String name() {
                return "铁锭";
            }

            @Override
            public String mod() {
                return "minecraft";
            }

            @Override
            public String modName() {
                return "Minecraft";
            }

            @Override
            public Collection<String> tags() {
                return List.of();
            }

            @Override
            public String tooltip() {
                return "能合成铁块";
            }

            @Override
            public String id() {
                return "minecraft:iron_ingot";
            }

            @Override
            public long count() {
                return 3;
            }
        };
        assertTrue(StorageSearch.parse("tied").test(iron));
        assertTrue(StorageSearch.parse("$tiekuai").test(iron));
        assertTrue(StorageSearch.parse("td -jin").test(iron));
        assertFalse(StorageSearch.parse("jind").test(iron));
    }
}
