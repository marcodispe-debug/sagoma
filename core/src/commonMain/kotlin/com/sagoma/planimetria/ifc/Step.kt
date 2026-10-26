package com.sagoma.planimetria.ifc

/*
 * Formato dei file IFC (STEP, ISO 10303-21): righe "#12=IFCWALL('…',#5,'Muro',$,…);". Qui c'è solo quello che serve a
 * sagoma per scriverli e rileggerli, senza librerie esterne, così gira uguale su telefono, computer e browser.
 */

/** Valore di un attributo STEP. */
sealed interface StepValue {
    /** `#12`: rimando a un'altra riga. */
    data class Ref(val id: Int) : StepValue
    data class Str(val text: String) : StepValue
    /** `.ELEMENT.`, `.T.`, `.F.` */
    data class Enum(val name: String) : StepValue
    data class Num(val value: Double) : StepValue
    data class Lst(val items: List<StepValue>) : StepValue
    /** `IFCLABEL('…')`: valore con il suo tipo (nelle proprietà). */
    data class Typed(val type: String, val value: StepValue) : StepValue
    /** `$` (assente) e `*` (ricavato). */
    data object Null : StepValue
}

/** Una riga del file: `#id=TIPO(attributi);`. */
class StepEntity(val id: Int, val type: String, val args: List<StepValue>) {
    fun ref(i: Int): Int? = (args.getOrNull(i) as? StepValue.Ref)?.id
    fun str(i: Int): String? = when (val v = args.getOrNull(i)) {
        is StepValue.Str -> v.text
        is StepValue.Typed -> (v.value as? StepValue.Str)?.text
        else -> null
    }
    fun num(i: Int): Double? = when (val v = args.getOrNull(i)) {
        is StepValue.Num -> v.value
        is StepValue.Typed -> (v.value as? StepValue.Num)?.value
        else -> null
    }
    fun enum(i: Int): String? = (args.getOrNull(i) as? StepValue.Enum)?.name
    fun list(i: Int): List<StepValue> = (args.getOrNull(i) as? StepValue.Lst)?.items ?: emptyList()
    fun refs(i: Int): List<Int> = list(i).mapNotNull { (it as? StepValue.Ref)?.id }
    fun nums(i: Int): List<Double> = list(i).mapNotNull { (it as? StepValue.Num)?.value }
}

/** Il file letto: le righe per id. */
class StepFile(val entities: Map<Int, StepEntity>) {
    operator fun get(id: Int?): StepEntity? = id?.let { entities[it] }
    fun ofType(vararg types: String): List<StepEntity> = entities.values.filter { it.type in types }

    companion object {
        /** Legge il testo di un file IFC. Le righe che non capisce le salta (non fa fallire tutto il file). */
        fun parse(text: String): StepFile {
            val data = text.indexOf("DATA;").let { if (it < 0) 0 else it + 5 }
            val end = text.indexOf("ENDSEC;", data).let { if (it < 0) text.length else it }
            val out = HashMap<Int, StepEntity>()
            var i = data
            while (i < end) {
                val hash = text.indexOf('#', i)
                if (hash < 0 || hash >= end) break
                val stop = statementEnd(text, hash)
                runCatching { StepParser(text, hash, stop).entity() }.getOrNull()?.let { out[it.id] = it }
                i = stop + 1
            }
            return StepFile(out)
        }

        /** Posizione del `;` che chiude la riga che comincia in `from` (ignorando quelli dentro le stringhe). */
        private fun statementEnd(t: String, from: Int): Int {
            var i = from
            var inStr = false
            while (i < t.length) {
                val c = t[i]
                if (c == '\'') {
                    if (inStr && i + 1 < t.length && t[i + 1] == '\'') i++ else inStr = !inStr
                } else if (c == ';' && !inStr) return i
                i++
            }
            return t.length
        }
    }
}

private class StepParser(val t: String, var i: Int, val end: Int) {
    fun entity(): StepEntity {
        expect('#')
        val id = number().toInt()
        skip(); expect('=')
        skip()
        val type = word()
        val args = list()
        return StepEntity(id, type.uppercase(), args.items)
    }

    private fun skip() { while (i < end && t[i].isWhitespace()) i++ }
    private fun expect(c: Char) { skip(); require(i < end && t[i] == c) { "atteso $c" }; i++ }
    private fun word(): String { val s = i; while (i < end && (t[i].isLetterOrDigit() || t[i] == '_')) i++; return t.substring(s, i) }
    private fun number(): Double {
        val s = i
        while (i < end && (t[i].isDigit() || t[i] in "+-.eE")) i++
        return t.substring(s, i).toDouble()
    }

    private fun list(): StepValue.Lst {
        expect('(')
        val items = mutableListOf<StepValue>()
        skip()
        if (t[i] == ')') { i++; return StepValue.Lst(items) }
        while (true) {
            items += value()
            skip()
            when (t[i]) {
                ',' -> i++
                ')' -> { i++; return StepValue.Lst(items) }
                else -> error("separatore")
            }
        }
    }

    private fun value(): StepValue {
        skip()
        val c = t[i]
        return when {
            c == '#' -> { i++; StepValue.Ref(number().toInt()) }
            c == '\'' -> StepValue.Str(string())
            c == '.' -> { i++; val s = i; while (t[i] != '.') i++; i++; StepValue.Enum(t.substring(s, i - 1)) }
            c == '$' || c == '*' -> { i++; StepValue.Null }
            c == '(' -> list()
            c.isDigit() || c == '-' || c == '+' -> StepValue.Num(number())
            c == '"' -> { i++; val s = i; while (t[i] != '"') i++; i++; StepValue.Str(t.substring(s, i - 1)) }
            c.isLetter() -> {
                val type = word().uppercase()
                val inner = list().items
                StepValue.Typed(type, inner.firstOrNull() ?: StepValue.Null)
            }
            else -> error("valore")
        }
    }

    private fun string(): String {
        i++
        val sb = StringBuilder()
        while (i < end) {
            val c = t[i]
            if (c == '\'') {
                if (i + 1 < end && t[i + 1] == '\'') { sb.append('\''); i += 2; continue }
                i++
                break
            }
            sb.append(c)
            i++
        }
        return decode(sb.toString())
    }

    /** Lettere accentate scritte come `\X2\00E8\X0\` (e `\S\` per i caratteri latini alti). */
    private fun decode(s: String): String {
        if ('\\' !in s) return s
        val out = StringBuilder()
        var k = 0
        while (k < s.length) {
            when {
                s.startsWith("\\X2\\", k) -> {
                    val stop = s.indexOf("\\X0\\", k + 4).let { if (it < 0) s.length else it }
                    s.substring(k + 4, stop).chunked(4).forEach { h -> h.toIntOrNull(16)?.let { out.append(it.toChar()) } }
                    k = stop + 4
                }
                s.startsWith("\\X\\", k) && k + 5 <= s.length -> {
                    s.substring(k + 3, k + 5).toIntOrNull(16)?.let { out.append(it.toChar()) }
                    k += 5
                }
                s.startsWith("\\S\\", k) && k + 3 < s.length -> { out.append((s[k + 3].code + 128).toChar()); k += 4 }
                s.startsWith("\\\\", k) -> { out.append('\\'); k += 2 }
                else -> { out.append(s[k]); k++ }
            }
        }
        return out.toString()
    }
}

/** Scrive le righe del file una dopo l'altra, dando a ognuna il suo numero. */
class StepWriter {
    private val lines = StringBuilder()
    private var next = 1

    /** Aggiunge `TIPO(attributi già scritti)` e ne restituisce il rimando `#n`. */
    fun add(type: String, vararg args: String): String {
        val id = next++
        lines.append('#').append(id).append('=').append(type).append('(').append(args.joinToString(",")).append(");\n")
        return "#$id"
    }

    fun text(header: String): String = "ISO-10303-21;\nHEADER;\n$header\nENDSEC;\nDATA;\n$lines" + "ENDSEC;\nEND-ISO-10303-21;\n"

    companion object {
        const val NULL = "$"
        const val DERIVED = "*"

        /** Stringa STEP: apici raddoppiati, lettere oltre l'ASCII come `\X2\…\X0\`. */
        fun str(s: String?): String {
            if (s == null) return NULL
            val sb = StringBuilder("'")
            var wide = false
            for (c in s) {
                if (c.code in 32..126) {
                    if (wide) { sb.append("\\X0\\"); wide = false }
                    when (c) { '\'' -> sb.append("''"); '\\' -> sb.append("\\\\"); else -> sb.append(c) }
                } else {
                    if (!wide) { sb.append("\\X2\\"); wide = true }
                    sb.append(c.code.toString(16).uppercase().padStart(4, '0'))
                }
            }
            if (wide) sb.append("\\X0\\")
            return sb.append("'").toString()
        }

        /** Numero reale con il punto (STEP vuole sempre il punto: `3.`, `0.25`). */
        fun num(v: Double): String {
            val r = kotlin.math.round(v * 1e6) / 1e6
            val s = if (r == kotlin.math.floor(r) && kotlin.math.abs(r) < 1e15) "${r.toLong()}." else r.toString()
            return if (s == "-0.") "0." else s
        }

        fun list(items: List<String>): String = items.joinToString(",", "(", ")")
        fun enum(name: String) = ".$name."
        fun bool(v: Boolean) = if (v) ".T." else ".F."
    }
}
