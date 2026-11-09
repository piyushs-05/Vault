package com.piyush.vault.data.directory

import com.piyush.vault.domain.model.InstitutionDirectory
import java.util.Locale
import javax.inject.Inject

/** Small, editable starter directory. Sender aliases are hints, never bank authentication. */
class BundledInstitutionDirectory @Inject constructor() : InstitutionDirectory {
    private val aliases = linkedMapOf(
        "State Bank of India" to setOf("SBIINB","SBIUPI","SBIATM","ATMSBI","SBIPSG"),
        "HDFC Bank" to setOf("HDFCBK","HDFCBN"), "ICICI Bank" to setOf("ICICIB","ICICIT"),
        "Axis Bank" to setOf("AXISBK","AXISB"), "Kotak Mahindra Bank" to setOf("KOTAKB","KOTAK"),
        "Punjab National Bank" to setOf("PNBSMS","PNB"), "Bank of Baroda" to setOf("BOBTXN","BOBSMS"),
        "Canara Bank" to setOf("CANBNK","CANBKS"), "Union Bank of India" to setOf("UNIONB","UBISMS"),
        "Indian Bank" to setOf("INDBNK"), "IDFC FIRST Bank" to setOf("IDFCFB"),
        "Bank of India" to setOf("BOIIND"), "Central Bank of India" to setOf("CENTBK"),
    )
    override fun names() = aliases.keys.toList()
    override fun institutionForSender(sender: String): String? {
        val header = sender.uppercase(Locale.ROOT).replace(Regex("^[A-Z0-9]{2}-"),"").replace(Regex("-[A-Z]$"),"")
        return aliases.entries.firstOrNull { header in it.value }?.key
    }
}
