package com.sagoma.planimetria.editor

import com.sagoma.planimetria.model.Vec2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Verso dove va il prossimo muro nel "muro per muro" (sulla pianta: y cresce verso il basso). */
enum class WallArrow(val dir: Vec2) {
    RIGHT(Vec2(1.0, 0.0)),
    DOWN(Vec2(0.0, 1.0)),
    LEFT(Vec2(-1.0, 0.0)),
    UP(Vec2(0.0, -1.0)),
}

/** Perché freccia e angolo scritti non danno un muro. */
enum class WallDirectionProblem {
    /** La freccia torna indietro sopra il muro precedente. */
    BACKWARDS,
    /** Freccia dritta, nel verso del muro precedente, con un angolo: non si sa da che parte girare. */
    ANGLE_WITHOUT_SIDE,
    /** Angolo tra i muri fuori da 1–179°. */
    ANGLE_OUT_OF_RANGE,
}

/**
 * Direzione del prossimo muro da freccia e angolo TRA I DUE MURI (quello che si misura nell'angolo della stanza, 90° = in
 * squadra): la freccia dice da che parte gira il nuovo muro. Esempio: muro precedente verso destra, freccia giù, 70° → il
 * nuovo muro scende chiudendo un angolo di 70° con il precedente. Senza angolo (o per il primo muro) il muro va dritto
 * nella direzione della freccia.
 */
object WallDirection {
    const val MIN_ANGLE = 1.0
    const val MAX_ANGLE = 179.0

    sealed interface Result {
        data class Ok(val dir: Vec2) : Result
        data class Invalid(val problem: WallDirectionProblem) : Result
    }

    /** [previous] = direzione del muro precedente (null per il primo muro). */
    fun of(previous: Vec2?, arrow: WallArrow, cornerDeg: Double?): Result {
        val prev = previous?.normalized()?.takeIf { it.length > 0.5 }
        val parallel = prev != null && abs(prev.cross(arrow.dir)) < 1e-6
        if (parallel && (prev!! dot arrow.dir) < 0) return Result.Invalid(WallDirectionProblem.BACKWARDS)
        if (cornerDeg == null || prev == null) return Result.Ok(arrow.dir)
        if (cornerDeg < MIN_ANGLE || cornerDeg > MAX_ANGLE) return Result.Invalid(WallDirectionProblem.ANGLE_OUT_OF_RANGE)
        if (parallel) return Result.Invalid(WallDirectionProblem.ANGLE_WITHOUT_SIDE)
        // L'angolo tra i muri si apre a partire dal muro precedente percorso all'indietro, dalla parte della freccia.
        val back = prev * -1.0
        val a = cornerDeg * kotlin.math.PI / 180.0
        fun rot(t: Double) = Vec2(back.x * cos(t) - back.y * sin(t), back.x * sin(t) + back.y * cos(t))
        val one = rot(a)
        val other = rot(-a)
        return Result.Ok(if ((one dot arrow.dir) >= (other dot arrow.dir)) one else other)
    }
}
