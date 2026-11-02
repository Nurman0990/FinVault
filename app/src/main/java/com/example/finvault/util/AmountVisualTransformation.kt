package com.example.finvault.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.Locale

/**
 * AmountVisualTransformation — форматирует вводимые числа пробелами по тысячам.
 * Пример: "1000000" -> "1 000 000"
 */
class AmountVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val parts = originalText.split(".", ",")
        val intPart = parts[0]
        val formattedInt = if (intPart.isNotEmpty()) {
            "%,d".format(Locale("ru", "RU"), intPart.toLongOrNull() ?: 0).replace(",", " ")
        } else ""

        val formattedText = if (parts.size > 1) {
            "$formattedInt.${parts[1]}"
        } else {
            formattedInt
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formattedText.length
            override fun transformedToOriginal(offset: Int): Int = originalText.length
        }

        return TransformedText(AnnotatedString(formattedText), offsetMapping)
    }
}
