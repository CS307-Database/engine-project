package storage;

import edu.sustech.cs307.storage.LRUReplacer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

public class LRUReplacerTest {

    private LRUReplacer replacer;

    @BeforeEach
    void setUp() {
        replacer = new LRUReplacer(5);
        // 确保在 LRUReplacer 构造函数里有:
        // this.pinnedFrames = new HashSet<>();
    }

    /**
     * 测试当 LRUList 为空时调用 Victim。
     */
    @Test
    @DisplayName("空LRU测试")
    void testVictimWhenEmpty() {
        int victim = replacer.Victim();
        assertThat(victim).as("Victim should return -1 when LRUList is empty").isEqualTo(-1);
    }

    /**
     * 测试 Pin 逻辑:
     *  - 当 pinnedFrames + LRUList 未满时可以成功 Pin。
     *  - 当已经满时再次 Pin 应抛异常。
     */
    @Test
    @DisplayName("测试pin页面")
    void testPinFrame() {
        // Pin 三个页面，尚未超过最大值 5
        replacer.Pin(1);
        replacer.Pin(2);
        replacer.Pin(3);

        // 当前 size = pinnedFrames.size() + LRUList.size()
        // 由于都被 Pin，因此 size = 3
        assertThat(replacer.size()).isEqualTo(3);

        // 再 Pin 两个，不超限
        replacer.Pin(4);
        replacer.Pin(5);
        assertThat(replacer.size()).isEqualTo(5);


        // 尝试再 Pin，第六个时应当抛出异常
        assertThatThrownBy(() -> replacer.Pin(6))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("REPLACER IS FULL");
    }

    /**
     * 测试在 LRUList 中已经存在的 frame 重新 Pin。
     *  - 先 Unpin，再 Pin 会将其从 LRUList 中移除。
     */
    @Test
    @DisplayName("测试反复pin页面")
    void testPinExistingInLRU() {
        // Pin(1) => pinnedFrames 包含 1
        replacer.Pin(1);

        // Unpin(1) => pinnedFrames 移除 1，并将 1 加入到 LRUList
        replacer.Unpin(1);

        // 此时 1 在 LRUList 中，再 Pin(1) => 应该从 LRUList 移除，并从 LRUHash 删除
        replacer.Pin(1);

        // 此时 1 不在 LRUList 中
        int victim = replacer.Victim();
        // LRUList 为空，Victim 返回 -1
        assertThat(victim).isEqualTo(-1);
        assertThat(replacer.size()).isEqualTo(1);
        // pinnedFrames 里还有 1，所以 size = 1
    }

    /**
     * 测试 Unpin 操作:
     *  - 只能 Unpin 已经 Pin 过的页面，否则抛异常。
     *  - Unpin 成功后应将 frame 放到 LRUList。
     */
    @Test
    @DisplayName("测试 Unpin 操作")
    void testUnpinFrame() {
        replacer.Pin(1);
        replacer.Pin(2);

        // 正确 Unpin(1)
        replacer.Unpin(1);
        assertThat(replacer.size()).isEqualTo(2);
        // pinnedFrames = 1, LRUList = 1(包含 1)
        // 具体分布： pinnedFrames = {2}, LRUList = [1]

        assertThat(replacer.Victim()).isEqualTo(1);

        // 再次 Unpin(1)，应当抛出异常，因为 1 已经不在 replacer 中
        assertThatThrownBy(() -> replacer.Unpin(1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("UNPIN PAGE NOT FOUND");

        // Unpin(2) => 成功放入 LRUList
        replacer.Unpin(2);
        assertThat(replacer.size()).isEqualTo(1);
        // pinnedFrames 为空，LRUList = [2]
    }

    /**
     * 测试 Victim 行为：
     *  - 驱逐 LRUList 中的第一个元素 (最不常用)。
     */
    @Test
    @DisplayName("测试 Victim 行为")
    void testVictimLRU() {
        // Pin 并 Unpin: 让页面 1,2,3 进入 LRUList
        replacer.Pin(1);
        replacer.Pin(2);
        replacer.Pin(3);

        replacer.Unpin(1); // LRUList = [1]
        replacer.Unpin(2); // LRUList = [1, 2]
        replacer.Unpin(3); // LRUList = [1, 2, 3]
        assertThat(replacer.size()).isEqualTo(3);

        // Victim => 应移除列表首部 (1)
        int victim = replacer.Victim();
        assertThat(victim).isEqualTo(1);
        assertThat(replacer.size()).isEqualTo(2); // 还剩 [2, 3] + pinnedFrames(0)

        // 继续 Victim => 移除 2
        victim = replacer.Victim();
        assertThat(victim).isEqualTo(2);

        // 剩 [3]
        assertThat(replacer.size()).isEqualTo(1);
    }

    /**
     * 大规模数据测试:
     *  - 向 replacer 中 Pin 大量页面，然后逐一 Unpin。
     *  - 确认 size、Victim 行为正确。
     */
    @Test
    @DisplayName("大规模数据测试")
    void testLargeScaleData() {
        int capacity = 100;
        replacer = new LRUReplacer(capacity);

        // 先 Pin 满 capacity 个页面
        for (int i = 0; i < capacity; i++) {
            replacer.Pin(i);
        }
        assertThat(replacer.size()).as("All pinned, size should be 100").isEqualTo(capacity);

        // 全部 Unpin => 都变为可驱逐
        for (int i = 0; i < capacity; i++) {
            replacer.Unpin(i);
        }
        // pinnedFrames = 0, LRUList = 100
        assertThat(replacer.size()).isEqualTo(capacity);

        // Victim 一半
        for (int i = 0; i < capacity / 2; i++) {
            replacer.Victim();
        }
        // 应还剩 capacity/2
        assertThat(replacer.size()).isEqualTo(capacity / 2);

        // 再 Pin 一些 frame
        for (int i = capacity; i < capacity + 10; i++) {
            // 这时 pinnedFrames.size() + LRUList.size() <= capacity, 可以 Pin
            replacer.Pin(i);
        }
        // 现在有 capacity/2 (在LRU中) + 10 (pinned) = capacity/2 + 10
        assertThat(replacer.size()).isEqualTo(capacity / 2 + 10);
    }

    @Test
    @DisplayName("所有页面被固定时无法驱逐")
    void testAllPinnedNoVictim() {
        // 填充全部为固定页面
        IntStream.range(0, 5).forEach(i -> replacer.Pin(i));

        // 尝试获取 Victim 应失败
        assertThat(replacer.Victim()).isEqualTo(-1);

        // 解除一个固定并验证
        replacer.Unpin(3);
        assertThat(replacer.Victim()).isEqualTo(3);
    }
    

    @Test
    @DisplayName("混合操作后的LRU顺序验证")
    void testComplexAccessPattern() {
        // 1. 初始状态
        replacer.Pin(1);
        replacer.Pin(2);
        replacer.Unpin(1); // LRU: [1]
        replacer.Unpin(2); // LRU: [1, 2]

        // 2. 再次访问1，应移至末尾
        replacer.Pin(1);   // 从LRU移除
        replacer.Unpin(1); // 重新加入末尾 → LRU: [2, 1]

        // 3. 验证淘汰顺序
        assertThat(replacer.Victim()).isEqualTo(2);
        assertThat(replacer.Victim()).isEqualTo(1);
    }

    @Test
    @DisplayName("容量满时固定新页面应失败")
    void testPinBeyondCapacity() {
        // 填满容量
        IntStream.range(0, 5).forEach(i -> {
            replacer.Pin(i);
            replacer.Unpin(i);
        });

        // 尝试固定第6个页面
        assertThatThrownBy(() -> replacer.Pin(5))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("REPLACER IS FULL");
    }

    @Test
    @DisplayName("重复固定同一页面不应改变状态")
    void testDuplicatePin() {
        replacer.Pin(1);
        replacer.Pin(1); // 重复固定

        // 解除固定后应能驱逐
        replacer.Unpin(1);
        assertThat(replacer.Victim()).isEqualTo(1);
    }

    @Test
    @DisplayName("淘汰后页面状态应彻底移除")
    void testVictimRemovesCompletely() {
        replacer.Pin(1);
        replacer.Unpin(1);
        replacer.Victim(); // 驱逐1

        // 验证后续操作
        assertThatThrownBy(() -> replacer.Unpin(1))
                .isInstanceOf(RuntimeException.class);
        assertThat(replacer.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("交替固定/取消固定后的容量计算")
    void testAlternatingPinUnpinSize() {
        replacer.Pin(1);
        replacer.Unpin(1); // size=1 (LRU)
        replacer.Pin(1);   // size=1 (Pinned)
        replacer.Pin(2);   // size=2 (Pinned)
        replacer.Unpin(2); // size=2 (1 Pinned, 1 LRU)

        assertThat(replacer.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("批量操作后的内部一致性")
    void testBulkOperationsConsistency() {
        // 批量添加
        IntStream.range(0, 5).forEach(i -> {
            replacer.Pin(i);
            replacer.Unpin(i);
        });

        // 验证全部在LRU
        assertThat(replacer.size()).isEqualTo(5);
        assertThat(replacer.Victim()).isEqualTo(0);

        // 重新固定并解除
        replacer.Pin(1);
        replacer.Unpin(1);

        // 验证最新位置
        assertThat(replacer.Victim()).isEqualTo(2);
        assertThat(replacer.Victim()).isEqualTo(3);
        assertThat(replacer.Victim()).isEqualTo(4);
        assertThat(replacer.Victim()).isEqualTo(1); // 重新加入后变为最后
    }
}