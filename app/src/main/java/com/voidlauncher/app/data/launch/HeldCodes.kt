package com.voidlauncher.app.data.launch

/**
 * Which owner holds which key or mouse button code. Two owners can hold the same code (a finger on the attack
 * button and the camera finger both hold the left mouse button), so the code is pressed when the first owner takes
 * it and released only when the last owner lets go.
 *
 * This class has no Android dependency, so its counting can be unit tested. It is not thread safe.
 */
class HeldCodes {
    private val codesByOwner = HashMap<Any, MutableSet<Int>>()
    private val holdersByCode = HashMap<Int, Int>()

    /**
     * Records that [owner] holds [code], or lets it go. Returns true when the code went from released to pressed,
     * false when it went from pressed to released, and null when nothing changed.
     */
    fun set(owner: Any, code: Int, down: Boolean): Boolean? {
        val holds = codesByOwner[owner]?.contains(code) == true
        if (down == holds) return null
        val holders = holdersByCode[code] ?: 0
        return if (down) {
            val codes = codesByOwner.getOrPut(owner) { HashSet() }
            codes.add(code)
            holdersByCode[code] = holders + 1
            if (holders == 0) true else null
        } else {
            val codes = codesByOwner.getValue(owner)
            codes -= code
            if (codes.isEmpty()) codesByOwner.remove(owner)
            if (holders <= 1) holdersByCode.remove(code) else holdersByCode[code] = holders - 1
            if (holders <= 1) false else null
        }
    }

    /** Lets go of every code [owner] holds. Returns the codes that went from pressed to released. */
    fun releaseOwner(owner: Any): List<Int> {
        val codes = codesByOwner[owner]?.toList() ?: return emptyList()
        return codes.mapNotNull { code -> if (set(owner, code, false) == false) code else null }
    }

    /** Lets go of everything. Returns the codes that went from pressed to released. */
    fun releaseAll(): List<Int> = codesByOwner.keys.toList().flatMap { releaseOwner(it) }

    /** True while any owner holds [code]. */
    fun isHeld(code: Int): Boolean = holdersByCode.containsKey(code)
}
