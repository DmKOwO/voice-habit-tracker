package com.voicehabit.tracker.core.update.github

/**
 * Строгое SemVer 2.0 сравнение для проверки обновлений.
 *
 * Граничные случаи, которые здесь закрыты (и покрыты тестами):
 * - префикс `v` и пробелы (`" v1.2.3 "` → 1.2.3);
 * - неполные версии (`"1.2"` → 1.2.0);
 * - prerelease (`1.0.0-beta` < `1.0.0`, `alpha` < `beta` < `rc`);
 * - build-metadata (`+build`) игнорируется при сравнении;
 * - мусор (`"latest"`, `""`) → null, а не падение.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return comparePrerelease(prerelease, other.prerelease)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + (prerelease?.let { "-$it" } ?: "")

    companion object {
        private val PATTERN = Regex(
            """^v?\s*(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?\s*$"""
        )

        fun parse(raw: String?): SemVer? {
            if (raw.isNullOrBlank()) return null
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val numbers = match.groupValues
            return SemVer(
                major = numbers[1].toIntOrNull() ?: return null,
                minor = numbers[2].ifEmpty { "0" }.toIntOrNull() ?: return null,
                patch = numbers[3].ifEmpty { "0" }.toIntOrNull() ?: return null,
                prerelease = numbers[4].ifEmpty { null }
            )
        }

        private fun comparePrerelease(left: String?, right: String?): Int {
            if (left == right) return 0
            // Релиз старше любого prerelease с тем же номером.
            if (left == null) return 1
            if (right == null) return -1
            val leftParts = left.split(".")
            val rightParts = right.split(".")
            for (i in 0 until maxOf(leftParts.size, rightParts.size)) {
                val l = leftParts.getOrNull(i) ?: return -1
                val r = rightParts.getOrNull(i) ?: return 1
                if (l == r) continue
                val lNum = l.toIntOrNull()
                val rNum = r.toIntOrNull()
                if (lNum != null && rNum != null) {
                    if (lNum != rNum) return lNum.compareTo(rNum)
                } else {
                    return l.compareTo(r)
                }
            }
            return 0
        }
    }
}
