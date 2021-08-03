package com.example.sqr

import android.app.AlertDialog
import android.content.Context
import com.budiyev.android.codescanner.CodeScanner

class MsgBox {
    companion object {
        private lateinit var dialog: AlertDialog

        // Displays message with "Ok" button
        fun ok(
            ctx: Context,
            title: String,
            msg: String,
            callOk: () -> Unit
        ) {
            dialog = AlertDialog.Builder(ctx) // Pass a reference to your main activity here
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Ok") { _, _ ->
                    callOk()
                }.setOnDismissListener { callOk() }
        .show();
    }

        // same width "Cancel" and "Ok" buttons
        fun okCancel (
            ctx: Context,
            scn: CodeScanner,
            title: String,
            msg: String,
            okStr: String,
            callOk: () -> Unit
        ) {
            dialog = AlertDialog.Builder(ctx) // Pass a reference to your main activity here
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(okStr) { _, _ ->
                    callOk()
                }.setNegativeButton(ctx.getString(R.string.cancel)) { _, _ ->
                    dialog.cancel()
                    scn.startPreview()
                }.setOnDismissListener {
                    scn.startPreview()
                }
                .show();
        }
    }
}