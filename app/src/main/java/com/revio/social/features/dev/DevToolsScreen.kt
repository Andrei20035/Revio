package com.revio.social.features.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.revio.social.BuildConfig
import com.revio.social.core.analytics.CrashContext
import com.revio.social.core.ui.theme.Poppins

/** How long [forceAnr] blocks the main thread — well past Android's ~5s input-dispatch ANR timeout. */
private const val ANR_BLOCK_MILLIS = 15_000L

/**
 * pas 1.10 — declanșează manual un crash / non-fatal / ANR pentru a verifica end-to-end, înainte
 * de closed testing, că toate trei ajung în consola Crashlytics. Doar debug: neînregistrat în
 * graful de navigație pentru build-urile de release (vezi RevioNavigation.kt) și accesibil doar
 * din secțiunea Developer, vizibilă tot doar în debug, din Settings.
 */
@Composable
fun DevToolsScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05081D))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Dev Tools",
                    color = Color.White,
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = ::forceCrash) {
                    Text("Force crash")
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = ::forceNonFatal) {
                    Text("Force non-fatal")
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = ::forceAnr) {
                    Text("Force ANR")
                }
            }
        }
    }
}

/** pas 6.4 — verificarea manuală din DebugView/Crashlytics are nevoie de cel puțin un custom
 * key pe fiecare raport ca să confirme că [CrashContext] chiar ajunge la Crashlytics; nu e
 * instrumentare de flow reală (asta rămâne un pas separat, CrashContext e altfel neconectat). */
private fun tagDevToolsContext(stage: String) {
    CrashContext.setFlow("dev_tools")
    CrashContext.setStage(stage)
    CrashContext.setBuildType(if (BuildConfig.DEBUG) "debug" else "release")
}

private fun forceCrash() {
    tagDevToolsContext("force_crash")
    throw RuntimeException("Forced crash — pas 1.10 Dev Tools")
}

private fun forceNonFatal() {
    tagDevToolsContext("force_non_fatal")
    FirebaseCrashlytics.getInstance().recordException(Exception("Forced non-fatal — pas 1.10 Dev Tools"))
}

private fun forceAnr() {
    tagDevToolsContext("force_anr")
    Thread.sleep(ANR_BLOCK_MILLIS)
}
