/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.example.android.biometricauth

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private lateinit var textInputView: AppCompatEditText
    private lateinit var textOutputView: AppCompatTextView
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var readyToEncrypt: Boolean = false
    private lateinit var cryptographyManager: CryptographyManager
    private lateinit var secretKeyName: String
    private lateinit var ciphertext: ByteArray
    private lateinit var initializationVector: ByteArray

    private lateinit var sharedPreferences: SharedPreferences


    private var isType1 = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = getSharedPreferences("example_pref", Context.MODE_PRIVATE)
        cryptographyManager = cryptographyManager()
        // e.g. secretKeyName = "biometric_sample_encryption_key"
        secretKeyName = getString(R.string.secret_key_name)
        biometricPrompt = createBiometricPrompt()
        promptInfo = createPromptInfo()

        textInputView = findViewById(R.id.input_view)
        textOutputView = findViewById(R.id.output_view)
        findViewById<Button>(R.id.encrypt_button).setOnClickListener {
            isType1 = true
            authenticateToEncrypt()
        }
        findViewById<Button>(R.id.decrypt_button).setOnClickListener {
            isType1 = true
            authenticateToDecrypt()
        }

        findViewById<Button>(R.id.encrypt_button2).setOnClickListener {
            isType1 = false
            authenticateToEncrypt()
        }
        findViewById<Button>(R.id.decrypt_button2).setOnClickListener {
            isType1 = false
            authenticateToDecrypt()
        }
    }

    private fun createBiometricPrompt(): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d(TAG, "$errorCode :: $errString")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Authentication failed for an unknown reason")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Authentication was successful")
                processData(result.cryptoObject)
            }
        }

        //The API requires the client/Activity context for displaying the prompt view
        return BiometricPrompt(this, executor, callback)
    }

    private fun createPromptInfo(): BiometricPrompt.PromptInfo {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.prompt_info_title)) // e.g. "Sign in"
                .setSubtitle(getString(R.string.prompt_info_subtitle)) // e.g. "Biometric for My App"
                .setDescription(getString(R.string.prompt_info_description)) // e.g. "Confirm biometric to continue"
                .setConfirmationRequired(false)
                .setNegativeButtonText(getString(R.string.prompt_info_use_app_password)) // e.g. "Use Account Password"
                // .setDeviceCredentialAllowed(true) // Allow PIN/pattern/password authentication.
                // Also note that setDeviceCredentialAllowed and setNegativeButtonText are
                // incompatible so that if you uncomment one you must comment out the other
                .build()
        return promptInfo
    }

    private fun authenticateToEncrypt() {
        readyToEncrypt = true
        if (BiometricManager.from(applicationContext).canAuthenticate() == BiometricManager
                        .BIOMETRIC_SUCCESS) {

            val ivBytes = try {
                IV.byteArray()
            } catch (e: Exception) {
                null
            }
            val cipher = cryptographyManager.getInitializedCipherForEncryption(secretKeyName, ivBytes)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun authenticateToDecrypt() {
        readyToEncrypt = false
        if (BiometricManager.from(applicationContext).canAuthenticate() == BiometricManager
                        .BIOMETRIC_SUCCESS) {
            val cipher = cryptographyManager.getInitializedCipherForDecryption(secretKeyName, IV.byteArray())
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }

    }

    private fun processData(cryptoObject: BiometricPrompt.CryptoObject?) {
        val data = if (readyToEncrypt) {
            val text = textInputView.text.toString()
            val encryptedData = cryptographyManager.encryptData(text, cryptoObject?.cipher!!)
            ciphertext = encryptedData.ciphertext
            initializationVector = encryptedData.initializationVector

            saveEncryptedText(ciphertext, initializationVector)
            ciphertext.string()
        } else {
            cryptographyManager.decryptData(getEncryptedText(), cryptoObject?.cipher!!)
        }
        textOutputView.text = data
    }

    companion object {
        private const val TAG = "BiometricF"
        private const val PREF_KEY = "key"
        private const val PREF_KEY_2 = "key2"

        private const val SEPARATOR = "]"

        private const val IV = "7HphhPrGt4BvAXFF"
    }

    private fun log(msg: String) {
        Log.e(TAG, msg)
    }

    private fun saveEncryptedText(encrypted: ByteArray, iv: ByteArray) {
        val text = encrypted.string()
        val ivText = iv.string()
        log("iv:$ivText")
        sharedPreferences.put(getPreferenceKey(), "$ivText$SEPARATOR$text")
    }

    private fun getEncryptedText(): ByteArray {
        val encrypted = sharedPreferences.get(getPreferenceKey(), "")
        return encrypted.split(SEPARATOR)[1].byteArray()
    }

//    private fun getEncryptedIv(): ByteArray {
//        val encrypted = sharedPreferences.get(getPreferenceKey(), "")
//        return encrypted.split(SEPARATOR)[0].byteArray()
//    }

    private fun getPreferenceKey(): String = if (isType1) {
        PREF_KEY
    } else {
        PREF_KEY_2
    }

    private fun ByteArray.string() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.byteArray() = Base64.decode(this, Base64.NO_WRAP)
}
