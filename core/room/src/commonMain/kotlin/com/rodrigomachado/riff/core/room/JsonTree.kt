package com.rodrigomachado.riff.core.room

import org.json.JSONArray
import org.json.JSONObject

/**
 * A cópia local do que se observa, montada com os eventos do streaming como o Firebase faz: `put` troca o valor no
 * caminho, `patch` troca só os filhos dados; null apaga, e um objeto que fica vazio some (no Firebase não existe
 * objeto vazio). Nada é alterado no lugar: cada evento dá uma árvore nova, e a emitida antes continua valendo.
 * Uma lista (o Firebase manda assim um nó de chaves numéricas seguidas, como o `placar` das rodadas) que recebe um
 * evento num filho vira objeto com os índices como chaves, sem perder os itens.
 */
object JsonTree {
    fun put(tree: Any?, path: String, value: Any?): Any? = putAt(tree, segments(path), jsonValue(value))

    fun patch(tree: Any?, path: String, values: JSONObject): Any? {
        val base = segments(path)
        return values.keys().asSequence().toList().fold(tree) { current, key ->
            putAt(current, base + segments(key), jsonValue(values.opt(key)))
        }
    }

    private fun segments(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    private fun putAt(tree: Any?, path: List<String>, value: Any?): Any? {
        if (path.isEmpty()) return value.takeUnless { it is JSONObject && it.length() == 0 }
        val node = (tree as? JSONArray)?.let(::objectOf) ?: tree as? JSONObject
        val key = path.first()
        val child = putAt(node?.opt(key)?.let(::jsonValue), path.drop(1), value)
        val copy = JSONObject()
        node?.keys()?.forEach { copy.put(it, node.get(it)) }
        if (child == null) copy.remove(key) else copy.put(key, child)
        return copy.takeIf { it.length() > 0 }
    }

    private fun objectOf(list: JSONArray) = JSONObject().also { copy ->
        for (i in 0 until list.length()) jsonValue(list.opt(i))?.let { copy.put(i.toString(), it) }
    }
}
