package io.github.jingu.idea_qiq_plugin.ui

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

object QiqBundle : DynamicBundle("messages.QiqBundle") {

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = "messages.QiqBundle") key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
