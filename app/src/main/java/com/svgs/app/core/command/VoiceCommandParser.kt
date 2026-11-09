package com.svgs.app.core.command

import java.util.Locale

class VoiceCommandParser {

    fun parse(spokenText: String): Command? {
        val normalizedText = spokenText
            .lowercase(Locale.ROOT)
            // Normalize common Urdu/Arabic character variations.
            .replace('ك', 'ک')
            .replace('ي', 'ی')
            // Remove vowel marks.
            .replace(Regex("\\p{M}+"), "")
            // Preserve letters in both English and Urdu.
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return when (normalizedText) {
            "start object detection",
            "object detection",
            "detect objects",
            "detect object",
            "start detection",
            "چیزیں پہچانو",
            "چیزیں پہچانیں",
            "سامنے کیا ہے",
            "اشیا کی شناخت",
            "اشیاء کی شناخت" -> Command.StartObjectDetection

            "read text",
            "read the text",
            "start reading",
            "scan text",
            "متن پڑھو",
            "متن پڑھیں",
            "لکھا ہوا پڑھو",
            "لکھا ہوا پڑھیں",
            "یہ پڑھو" -> Command.ReadText

            "scan currency",
            "can currency",
            "scan the currency",
            "recognize currency",
            "identify currency",
            "check currency",
            "currency",
            "scan money",
            "check money",
            "پیسے پہچانو",
            "پیسے پہچانیں",
            "نوٹ پہچانو",
            "نوٹ پہچانیں",
            "کرنسی پہچانو",
            "کرنسی چیک کرو" -> Command.ScanCurrency

            "check battery",
            "check the battery",
            "battery status",
            "battery level",
            "battery",
            "what is my battery level",
            "بیٹری",
            "بیٹری چیک کرو",
            "بیٹری چیک کریں",
            "بیٹری کتنی ہے",
            "بیٹری بتاؤ" -> Command.CheckBattery

            "stop",
            "stop detection",
            "stop reading",
            "stop scanning",
            "رک جاؤ",
            "رک جائیں",
            "بند کرو",
            "بند کریں",
            "روک دو" -> Command.Stop

            else -> null
        }
    }
}