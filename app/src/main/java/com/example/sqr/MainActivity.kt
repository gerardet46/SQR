package com.example.sqr

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.budiyev.android.codescanner.*
import com.google.zxing.BarcodeFormat
import kotlin.system.exitProcess
import android.os.Parcelable
import android.provider.CalendarContract
import android.provider.ContactsContract
import it.auron.library.geolocation.GeoCardParser
import it.auron.library.mecard.MeCardParser
import it.auron.library.vcard.VCardParser
import it.auron.library.vevent.VEventParser
import it.auron.library.wifi.WifiCardParser


class MainActivity : AppCompatActivity() {
    lateinit var scn: CodeScanner
    val rgx: Array<Regex> = arrayOf(
        Regex("""^((www\.)|(https?:))\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()!@:%_\+.~#?&\/\/=]*)$""", RegexOption.IGNORE_CASE),
        Regex("""^sms(to):""", RegexOption.IGNORE_CASE),
        Regex("""^mms(to):""", RegexOption.IGNORE_CASE),
        Regex("""^tel:""", RegexOption.IGNORE_CASE),
    )
    val rgxPrefix: Array<String> = arrayOf(
        "", // blank for url  (full match)
        "sms:",
        "mms:",
        "tel:",
    )
    val rgxInitIndex = 1 // first not-blank index to test regex

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // check camera permission
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 123)
        } else {
            startScanning()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 123) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // granted
                startScanning()
            } else {
                // denied
                MsgBox.ok(this, "Error", getString(R.string.permission_error)) {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(1)
                }
            }
        }
    }

    private fun startScanning() {
        // Parameters (default values)
        val scnView: CodeScannerView = findViewById(R.id.scnView)

        scn = CodeScanner(this, scnView)
        scn.camera = CodeScanner.CAMERA_BACK
        scn.formats = listOf(/*BarcodeFormat.PDF_417, */BarcodeFormat.QR_CODE)
        scn.autoFocusMode = AutoFocusMode.SAFE // or CONTINUOUS
        scn.scanMode = ScanMode.SINGLE // SINGLE or CONTINUOUS or PREVIEW
        scn.isAutoFocusEnabled = true // Whether to enable auto focus or not
        scn.isFlashEnabled = false // Whether to enable flash or not

        scn.decodeCallback = DecodeCallback {
            runOnUiThread {
                scn.stopPreview()
                scn.releaseResources()

                // parse result
                parse(it.text)
            }
        }
        scn.errorCallback = ErrorCallback { // or ErrorCallback.SUPPRESS
            runOnUiThread {
                Toast.makeText(
                    this, getString(R.string.cam_error) + it.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if(::scn.isInitialized) {
            scn.startPreview()
        }
    }

    override fun onPause() {
        if(::scn.isInitialized) {
            scn.releaseResources()
        }
        super.onPause()
    }

    // parse info from scanner
    fun parse(txt: String) {
        var text: String = txt

        // check if it's website
        println(txt)
        if (rgx[0].matches(txt)) {
            MsgBox.okCancel(this, scn, getString(R.string.web_title), txt, getString(R.string.web_go)) {
                scn.startPreview()

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(txt))
                println(intent)
                startActivity(intent)
                /*if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    println("si")
                }
                else println("no")*/
            }
            return
        }
        // not a webpage
        else if (WifiCardParser.isWifi(txt)) {
            val wCard = WifiCardParser.parse(txt)
            val enc: String = wCard.type
            val ssid: String = wCard.sid
            val passwd: String = wCard.password

            MsgBox.okCancel(
                this,
                scn,
                getString(R.string.wifi_title),
                "${getString(R.string.nom)}: $ssid\n${getString(R.string.wifi_enc)}: $enc\n${getString(R.string.wifi_key)}: $passwd", getString(R.string.wifi_connect))
            {
                scn.startPreview()

                /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // load wifi suggestion
                    val wf: WifiNetworkSuggestion
                    if (enc == "nopass")
                        wf = WifiNetworkSuggestion.Builder()
                            .setSsid(ssid)
                            .build()
                    else
                        wf = WifiNetworkSuggestion.Builder()
                            .setSsid(ssid)
                            .setWpa2Passphrase(passwd)
                            .build()

                    val wcList: Array<WifiNetworkSuggestion> = arrayOf(wf)
                    val bundle = Bundle()
                    bundle.putParcelableArrayList(
                        Settings.EXTRA_WIFI_NETWORK_LIST,
                        wcList as ArrayList<out Parcelable?>?
                    )
                    val intent: Intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    intent.putExtras(bundle)
                    //if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    //}
                }
                else {*/
                    // copy text
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("key", passwd)
                    clipboard.setPrimaryClip(clip)

                    Toast.makeText(this, getString(R.string.wifi_copied), Toast.LENGTH_SHORT).show()

                    val intent: Intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    //if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    //}
                //}
            }
            return
        }
        else if (MeCardParser.isMeCard(txt)) {
            val meCard = MeCardParser.parse(txt)

            MsgBox.okCancel(
                this,
                scn,
                getString(R.string.card_contact),
                "${getString(R.string.nom)}: ${meCard.name}\n${getString(R.string.telf)}: ${meCard.telephones[0]}\n...", getString(R.string.add))
            {
                scn.startPreview()
                val intent = Intent(Intent.ACTION_INSERT, Uri.parse(txt)).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE

                    val name = meCard.name
                    val email = meCard.email ?: ""
                    val phone = if (meCard.telephones.size > 0) meCard.telephones?.get(0) else ""
                    val phone2 = if (meCard.telephones.size > 1) meCard.telephones?.get(1) else ""
                    val company = meCard.org ?: ""

                    putExtra(ContactsContract.Intents.Insert.NAME, name)
                    putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                    putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                    putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, phone2)
                    putExtra(ContactsContract.Intents.Insert.COMPANY, company)
                }
                //if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                //}
            }
        }
        else if (VCardParser.isVCard(txt)) {
            // DOCS: https://reposhub.com/android/qrcode/RurioLuca-QrCardParsing.html
            val vCard = VCardParser.parse(txt)

            MsgBox.okCancel(
                this,
                scn,
                getString(R.string.card_contact),
                "${getString(R.string.nom)}: ${vCard.name}\n${getString(R.string.telf)}: ${vCard.telephones[0]}\n...", getString(R.string.add))
            {
                scn.startPreview()
                val intent = Intent(Intent.ACTION_INSERT, Uri.parse(txt)).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE

                    val name = vCard.name
                    val email = if (vCard.emails.size > 0) vCard.emails?.get(0) else ""
                    val phone = if (vCard.telephones.size > 0) vCard.telephones?.get(0) else ""
                    val phone2 = if (vCard.telephones.size > 1) vCard.telephones?.get(1) else ""
                    val company = vCard.company ?: ""

                    putExtra(ContactsContract.Intents.Insert.NAME, name)
                    putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                    putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                    putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, phone2)
                    putExtra(ContactsContract.Intents.Insert.COMPANY, company)
                }
                //if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                //}
            }
        }
        else if (VEventParser.isVEvent(txt)) {
            val vEvent = VEventParser.parse(txt)

            MsgBox.okCancel(
                this,
                scn,
                getString(R.string.ev),
                "${getString(R.string.title)}: ${vEvent.summary}\n${getString(R.string.ev_loc)}: ${vEvent.location}\n${getString(R.string.ev_starts)}: ${vEvent.dtStart}\n${getString(R.string.ev_end)}: ${vEvent.dtEnd}",
                getString(R.string.add))
            {
                scn.startPreview()
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, vEvent.summary ?: "")
                    putExtra(CalendarContract.Events.EVENT_LOCATION, vEvent.location ?: "")
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, vEvent.dtStart)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, vEvent.dtEnd)
                }
                //if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                //}
            }
        }
        else if (GeoCardParser.isGeoCard(txt)) {
            val gCard = GeoCardParser.parse(txt)

            MsgBox.okCancel(this, scn, getString(R.string.geo), "LAT: ${gCard.lat}\nLON: ${gCard.lon}", getString(R.string.see)) {
                scn.startPreview()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("geo:${gCard.lat},${gCard.lon}")
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            }
        }
        else {
            // simple types: text, sms, tel
                // check type first
            var uriName: String = ""
            for (i in rgxInitIndex until rgx.size) {
                if (rgx[i].find(text) != null) {
                    if (i < 5) text = rgx[i].replaceFirst(text, "")
                    uriName = rgxPrefix[i]
                    break
                }
            }
            // parse type
            if (uriName == "sms:" || uriName == "mms:") {
                var matches =
                    Regex("""^([^:]+):(.+)$""", RegexOption.DOT_MATCHES_ALL).matchEntire(text)

                var telf = matches?.groups?.get(1)?.value ?: ""
                var body = matches?.groups?.get(2)?.value ?: ""
                var trimBody = body

                if (trimBody?.length > 300)
                    trimBody = body.substring(0, 300) + "..."

                MsgBox.okCancel(this, scn, "SMS", "${getString(R.string.sms_to)}: $telf\n${getString(R.string.msm_msg)}: $trimBody", getString(R.string.msg_send)) {
                    scn.startPreview()

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:" + telf.trim())
                        putExtra("sms_body", body)
                    }
                    //if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    //}
                }
            } else if (uriName == "tel:") {
                MsgBox.okCancel(this, scn, getString(R.string.telf), text, getString(R.string.call)) {
                    scn.startPreview()

                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$text")
                    }
                    //if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    //}
                }
            } else {
                // text only
                var trimTxt = txt

                if (trimTxt?.length > 300)
                    trimTxt = txt.substring(0, 300) + "..."

                MsgBox.okCancel(this, scn, getString(R.string.txt), trimTxt, getString(R.string.txt_copy)) {
                    scn.startPreview()

                    // copy text
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("key", txt)
                    clipboard.setPrimaryClip(clip)

                    Toast.makeText(this, getString(R.string.txt_copied), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}