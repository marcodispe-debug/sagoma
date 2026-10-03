package com.sagoma.planimetria.assets.remote

import com.sagoma.planimetria.assets.isPortableAssetPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Legge e valida un manifest. Non sa da dove arriva (nessun HTTP, nessun fornitore): riceve il testo JSON.
 *
 * Con [clientVersion] si dice che versione del contratto sa gestire l'app (confrontata con `minClient`); con
 * [minCatalogVersion] la `catalogVersion` più alta già vista (un manifest più vecchio è un tentativo di tornare
 * indietro: [ManifestParseResult.Incompatible]). La firma ha una struttura ma qui NON si verifica.
 *
 * Si segnalano tutti i problemi trovati, non solo il primo.
 */
object ManifestParser {
    private val json = Json

    fun parse(text: String, clientVersion: Int = Int.MAX_VALUE, minCatalogVersion: Long = 0): ManifestParseResult {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            return ManifestParseResult.Invalid(listOf("not valid JSON"))
        }
        val obj = root as? JsonObject ?: return ManifestParseResult.Invalid(listOf("manifest is not a JSON object"))
        val issues = ArrayList<String>()

        val schema = obj.int("schema", issues, "schema")
        if (schema != null && schema != SUPPORTED_MANIFEST_SCHEMA) {
            return ManifestParseResult.Incompatible("unsupported schema $schema (supported: $SUPPORTED_MANIFEST_SCHEMA)")
        }
        val catalog = obj.string("catalog", issues, "catalog")
        val catalogVersion = obj.long("catalogVersion", issues, "catalogVersion")
        if (catalogVersion != null && catalogVersion < 1) issues += "catalogVersion must be >= 1"
        val releaseId = obj.string("releaseId", issues, "releaseId")
        if (releaseId != null && !isValidReleaseId(releaseId)) issues += "releaseId is not valid"
        val publishedAt = obj.optString("publishedAt", issues)
        val minClient = if ("minClient" in obj) obj.int("minClient", issues, "minClient") else 0
        if (minClient != null && minClient < 0) issues += "minClient must be >= 0"

        val layout = parseLayout(obj["blobs"], issues)
        if (layout == null && issues.isEmpty()) return ManifestParseResult.Incompatible("unknown blob layout")

        val assets = parseAssets(obj["assets"], issues)
        val aliases = parseAliases(obj["aliases"], issues)
        val shards = parseShards(obj["shards"], issues)
        val signature = parseSignature(obj["signature"], issues)

        if (assets != null) validateAssets(assets, aliases, issues)

        if (issues.isNotEmpty()) return ManifestParseResult.Invalid(issues)

        // Da qui tutto è presente e valido.
        if (minClient!! > clientVersion) return ManifestParseResult.Incompatible("requires client $minClient, this is $clientVersion")
        if (catalogVersion!! < minCatalogVersion) {
            return ManifestParseResult.Incompatible("catalogVersion $catalogVersion is older than the latest seen ($minCatalogVersion)")
        }
        return ManifestParseResult.Valid(
            Manifest(schema!!, catalog!!, catalogVersion, releaseId!!, publishedAt, minClient, layout!!, assets!!, aliases, shards, signature),
        )
    }

    // ---- sezioni ----

    private fun parseLayout(e: JsonElement?, issues: MutableList<String>): BlobLayout? {
        val o = e as? JsonObject
        if (o == null) {
            issues += "blobs is missing or not an object"
            return null
        }
        return when (val l = o.string("layout", issues, "blobs.layout")) {
            null -> null
            "flat" -> BlobLayout.Flat
            "fanout2" -> BlobLayout.Fanout2
            else -> null
        }
    }

    private fun parseAssets(e: JsonElement?, issues: MutableList<String>): List<ManifestAsset>? {
        val arr = e as? JsonArray
        if (arr == null) {
            issues += "assets is missing or not an array"
            return null
        }
        val out = ArrayList<ManifestAsset>(arr.size)
        arr.forEachIndexed { i, el ->
            val o = el as? JsonObject
            if (o == null) {
                issues += "assets[$i] is not an object"
                return@forEachIndexed
            }
            val where = "assets[$i]"
            val id = o.string("id", issues, "$where.id")
            val kind = o.string("kind", issues, "$where.kind")
            val label = if (id != null) "asset '$id'" else where
            val status = when (val s = o.optString("status", issues)) {
                null, "active" -> AssetStatus.Active
                "deprecated" -> AssetStatus.Deprecated
                "withdrawn" -> AssetStatus.Withdrawn
                else -> {
                    issues += "$label has unknown status '$s'"
                    AssetStatus.Active
                }
            }
            val access = when (val s = o.optString("access", issues)) {
                null, "public" -> AssetAccess.Public
                "protected" -> AssetAccess.Protected
                else -> {
                    issues += "$label has unknown access '$s'"
                    AssetAccess.Public
                }
            }
            val replacedBy = o.optString("replacedBy", issues)
            val tier = o.optString("tier", issues) ?: "standard"
            val requires = ArrayList<String>()
            when (val r = o["requires"]) {
                null, JsonNull -> {}
                is JsonArray -> r.forEach { x ->
                    val s = (x as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (s.isNullOrBlank()) issues += "$label has an invalid 'requires' entry" else requires += s
                }
                else -> issues += "$label: requires is not an array"
            }
            val files = ArrayList<ManifestFile>()
            val fa = o["files"] as? JsonArray
            if (fa == null || fa.isEmpty()) {
                issues += "$label has no files"
            } else {
                fa.forEachIndexed { j, fe -> parseFile(fe, "$label.files[$j]", issues)?.let { files += it } }
            }
            if (id != null && kind != null) out += ManifestAsset(id, kind, status, replacedBy, tier, requires, access, files)
        }
        return out
    }

    private fun parseFile(e: JsonElement, where: String, issues: MutableList<String>): ManifestFile? {
        val o = e as? JsonObject
        if (o == null) {
            issues += "$where is not an object"
            return null
        }
        val role = o.string("role", issues, "$where.role")
        val path = o.string("path", issues, "$where.path")
        if (path != null && !isPortableAssetPath(path)) issues += "$where.path is not a valid relative path"
        val sha = o.string("sha256", issues, "$where.sha256")
        if (sha != null && !isLowerHex64(sha)) issues += "$where.sha256 is not a 64-char lowercase hex SHA-256"
        val size = o.long("size", issues, "$where.size")
        if (size != null && size < 0) issues += "$where.size is negative"
        val mime = o.optString("mime", issues)
        return if (role != null && path != null && sha != null && size != null) ManifestFile(role, path, sha, size, mime) else null
    }

    private fun parseAliases(e: JsonElement?, issues: MutableList<String>): Map<String, String> {
        if (e == null || e is JsonNull) return emptyMap()
        val o = e as? JsonObject
        if (o == null) {
            issues += "aliases is not an object"
            return emptyMap()
        }
        val out = LinkedHashMap<String, String>()
        for ((k, v) in o) {
            val s = (v as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (s.isNullOrEmpty()) issues += "alias '$k' has no target" else out[k] = s
        }
        return out
    }

    private fun parseShards(e: JsonElement?, issues: MutableList<String>): List<ManifestShard> {
        if (e == null || e is JsonNull) return emptyList()
        val arr = e as? JsonArray
        if (arr == null) {
            issues += "shards is not an array"
            return emptyList()
        }
        val out = ArrayList<ManifestShard>()
        arr.forEachIndexed { i, el ->
            val o = el as? JsonObject
            if (o == null) {
                issues += "shards[$i] is not an object"
                return@forEachIndexed
            }
            val id = o.string("id", issues, "shards[$i].id")
            val path = o.string("path", issues, "shards[$i].path")
            if (path != null && !isPortableAssetPath(path)) issues += "shards[$i].path is not a valid relative path"
            val sha = o.string("sha256", issues, "shards[$i].sha256")
            if (sha != null && !isLowerHex64(sha)) issues += "shards[$i].sha256 is not a valid SHA-256"
            val size = o.long("size", issues, "shards[$i].size")
            if (size != null && size < 0) issues += "shards[$i].size is negative"
            if (id != null && path != null && sha != null && size != null) out += ManifestShard(id, path, sha, size)
        }
        return out
    }

    private fun parseSignature(e: JsonElement?, issues: MutableList<String>): ManifestSignature? {
        if (e == null || e is JsonNull) return null
        val o = e as? JsonObject
        if (o == null) {
            issues += "signature is not an object"
            return null
        }
        val alg = o.string("alg", issues, "signature.alg")
        val keyId = o.string("keyId", issues, "signature.keyId")
        val value = o.string("value", issues, "signature.value")
        return if (alg != null && keyId != null && value != null) ManifestSignature(alg, keyId, value) else null
    }

    // ---- coerenza ----

    private fun validateAssets(assets: List<ManifestAsset>, aliases: Map<String, String>, issues: MutableList<String>) {
        val byId = HashMap<String, ManifestAsset>()
        for (a in assets) {
            if (a.id.isBlank()) issues += "an asset has an empty id"
            if (a.kind.isBlank()) issues += "asset '${a.id}' has an empty kind"
            if (byId.put(a.id, a) != null) issues += "duplicate asset id '${a.id}'"
        }
        // Percorsi: lo stesso percorso può comparire più volte solo con lo stesso contenuto (sha e dimensione).
        val byPath = HashMap<String, ManifestFile>()
        val byLowerPath = HashMap<String, String>()
        for (a in assets) for (f in a.files) {
            val prev = byPath[f.path]
            if (prev == null) {
                byPath[f.path] = f
                val clash = byLowerPath.put(f.path.lowercase(), f.path)
                if (clash != null && clash != f.path) issues += "paths '$clash' and '${f.path}' differ only by case"
            } else if (prev.sha256 != f.sha256 || prev.size != f.size) {
                issues += "path '${f.path}' appears with different content"
            }
        }
        for (a in assets) {
            val roles = HashSet<String>()
            for (f in a.files) if (!roles.add(f.role)) issues += "asset '${a.id}' has two files with role '${f.role}'"
            val r = a.replacedBy
            if (r != null) {
                when {
                    a.status == AssetStatus.Active -> issues += "asset '${a.id}' is active but has replacedBy"
                    r == a.id -> issues += "asset '${a.id}' is replaced by itself"
                    r !in byId -> issues += "asset '${a.id}' is replaced by unknown asset '$r'"
                }
            }
        }
        // replacedBy non deve formare cicli.
        for (a in assets) {
            var cur: ManifestAsset? = a
            var steps = 0
            while (cur?.replacedBy != null && steps <= assets.size) {
                cur = byId[cur.replacedBy!!]
                steps++
            }
            if (cur?.replacedBy != null) {
                issues += "replacedBy cycle involving '${a.id}'"
                break
            }
        }
        for ((alias, target) in aliases) {
            if (alias in byId) issues += "alias '$alias' collides with an asset id"
            if (target !in byId) issues += "alias '$alias' points to unknown asset '$target'"
            if (target in aliases) issues += "alias '$alias' points to another alias"
        }
    }

    // ---- helper JSON ----

    private fun isValidReleaseId(s: String) =
        s.length in 1..128 && s.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' } && !s.startsWith(".")

    private fun isLowerHex64(s: String) = s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' }

    private fun JsonObject.string(key: String, issues: MutableList<String>, name: String): String? {
        val p = this[key] as? JsonPrimitive
        if (p == null || p is JsonNull || !p.isString) {
            issues += "$name is missing or not a string"
            return null
        }
        if (p.content.isEmpty()) {
            issues += "$name is empty"
            return null
        }
        return p.content
    }

    private fun JsonObject.optString(key: String, issues: MutableList<String>): String? {
        val e = this[key] ?: return null
        if (e is JsonNull) return null
        val p = e as? JsonPrimitive
        if (p == null || !p.isString) {
            issues += "$key is not a string"
            return null
        }
        return p.content
    }

    private fun JsonObject.long(key: String, issues: MutableList<String>, name: String): Long? {
        val p = this[key] as? JsonPrimitive
        val v = if (p == null || p is JsonNull || p.isString) null else p.longOrNull
        if (v == null) issues += "$name is missing or not an integer"
        return v
    }

    private fun JsonObject.int(key: String, issues: MutableList<String>, name: String): Int? {
        val v = long(key, issues, name) ?: return null
        if (v !in Int.MIN_VALUE..Int.MAX_VALUE) {
            issues += "$name is out of range"
            return null
        }
        return v.toInt()
    }
}
