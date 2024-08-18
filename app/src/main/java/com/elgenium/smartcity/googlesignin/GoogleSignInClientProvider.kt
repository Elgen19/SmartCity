package com.elgenium.smartcity.googlesignin

import android.content.Context
import com.elgenium.smartcity.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

object GoogleSignInClientProvider {
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.DEFAULT_CLIENT_ID) // Replace with your client ID
            .requestEmail()
            .build()

        return GoogleSignIn.getClient(context, googleSignInOptions)
    }
}