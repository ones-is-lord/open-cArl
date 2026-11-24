package dev.opencarl.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRecommenderTest {

    private val flagship = DeviceProfile(
        manufacturer = "Google",
        model = "Pixel 8 Pro",
        soc = "Tensor G3",
        totalRamMb = 12_000,
        availRamMb = 9_000,
        freeStorageMb = 60_000,
        bigCoreMhz = 2_900,
        cores = 9,
        npuTops = 45f,
    )

    private val mid = DeviceProfile(
        model = "Pixel 6a",
        soc = "Tensor",
        totalRamMb = 6_000,
        availRamMb = 2_200,
        freeStorageMb = 20_000,
        bigCoreMhz = 2_000,
        cores = 8,
        npuTops = 20f,
    )

    private val low = DeviceProfile(
        model = "Moto E",
        soc = "Snapdragon 4",
        totalRamMb = 2_500,
        availRamMb = 900,
        freeStorageMb = 4_000,
        bigCoreMhz = 1_400,
        cores = 4,
        npuTops = null,
    )

    @Test
    fun `tiers reflect memory and clocks`() {
        assertEquals(DeviceTier.FLAGSHIP, flagship.tier)
        assertEquals(DeviceTier.MID, mid.tier)
        assertEquals(DeviceTier.MINIMAL, low.tier)
    }

    @Test
    fun `an eight billion model does not fit on a six gigabyte phone`() {
        val llama = ModelCatalog.byId("llama-3.1-8b")!!
        val result = ModelRecommender.evaluate(mid, llama)

        assertEquals(Fit.NO, result.fit)
        assertTrue(result.reason.contains("not enough RAM"))
    }

    @Test
    fun `a mid range phone only gets models it can actually run`() {
        val recommendations = ModelRecommender.recommend(mid)

        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.all { it.fit != Fit.NO })
        assertTrue(recommendations.any { it.model.paramsB <= 2f })
        assertFalse(recommendations.any { it.model.paramsB > 4f })
    }

    @Test
    fun `flagship gets the large model in its recommendations`() {
        val recommendations = ModelRecommender.recommend(flagship, limit = 5)

        assertTrue(recommendations.any { it.model.id == "llama-3.1-8b" })
        assertTrue(recommendations.first().model.paramsB >= 4f)
    }

    @Test
    fun `storage shortage disqualifies a model that otherwise fits`() {
        val cramped = mid.copy(freeStorageMb = 1_000)
        val gemma = ModelCatalog.byId("gemma-2-2b")!!
        val result = ModelRecommender.evaluate(cramped, gemma)

        assertEquals(Fit.NO, result.fit)
        assertTrue(result.reason.contains("storage"))
    }

    @Test
    fun `throughput estimates are ordered by model size`() {
        val small = ModelRecommender.estimateTokensPerSecond(flagship, ModelCatalog.byId("llama-3.2-1b")!!)
        val large = ModelRecommender.estimateTokensPerSecond(flagship, ModelCatalog.byId("llama-3.1-8b")!!)

        assertTrue(small > large)
        assertTrue(small in 5f..60f)
        assertTrue(large > 0.4f)
    }

    @Test
    fun `thirty two bit devices are rejected`() {
        val legacy = mid.copy(abi = "armeabi-v7a")
        val result = ModelRecommender.evaluate(legacy, ModelCatalog.byId("llama-3.2-1b")!!)

        assertEquals(Fit.NO, result.fit)
        assertTrue(result.reason.contains("64-bit"))
    }

    @Test
    fun `npu table recognises common socs`() {
        assertEquals(45f, NpuHeuristics.estimateTops("Tensor G3"))
        assertEquals(36f, NpuHeuristics.estimateTops("SM8550"))
        assertEquals(null, NpuHeuristics.estimateTops("Some Unknown Chip"))
    }
}
