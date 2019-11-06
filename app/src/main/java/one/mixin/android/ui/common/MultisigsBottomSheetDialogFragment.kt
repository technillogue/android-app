package one.mixin.android.ui.common

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.fragment_multisigs_bottom_sheet.view.*
import kotlinx.android.synthetic.main.layout_pin_pb_error.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.R
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.response.MultisigsAction
import one.mixin.android.api.response.MultisigsState
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.common.biometric.BiometricItem
import one.mixin.android.ui.common.biometric.MultisigsBiometricItem
import one.mixin.android.vo.User
import one.mixin.android.widget.BottomSheet

class MultisigsBottomSheetDialogFragment : BiometricBottomSheetDialogFragment<MultisigsBiometricItem>() {
    companion object {
        const val TAG = "MultisigsBottomSheetDialogFragment"

        inline fun <reified T : BiometricItem> newInstance(t: T) =
            MultisigsBottomSheetDialogFragment().withArgs {
                putParcelable(ARGS_BIOMETRIC_ITEM, t)
            }
    }

    private val t: MultisigsBiometricItem by lazy {
        arguments!!.getParcelable<MultisigsBiometricItem>(ARGS_BIOMETRIC_ITEM)!!
    }

    private var success: Boolean = false

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        contentView = View.inflate(context, R.layout.fragment_multisigs_bottom_sheet, null)
        (dialog as BottomSheet).setCustomView(contentView)
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (t.action == MultisigsAction.cancel.name) {
            contentView.title.text = getString(R.string.multisig_revoke_transaction)
            contentView.arrow_iv.setImageResource(R.drawable.ic_multisigs_arrow_ban)
        } else {
            contentView.title.text = getString(R.string.multisig_transaction)
            contentView.arrow_iv.setImageResource(R.drawable.ic_multisigs_arrow_right)
        }
        contentView.sub_title.text = t.memo

        lifecycleScope.launch {
            val users = withContext(Dispatchers.IO) {
                bottomViewModel.findMultiUsers(t.senders, t.receivers)
            }
            if (users.isNotEmpty()) {
                val senders = arrayListOf<User>()
                val receivers = arrayListOf<User>()
                users.forEach { u ->
                    if (u.userId in t.senders) {
                        senders.add(u)
                    }
                    if (u.userId in t.receivers) {
                        receivers.add(u)
                    }
                }
                contentView.senders_view.addUserList(senders)
                contentView.receivers_view.addUserList(receivers)

                contentView.senders_view.setOnClickListener {
                    showUserList(senders, true)
                }
                contentView.receivers_view.setOnClickListener {
                    showUserList(receivers, false)
                }
            }
        }
    }

    override fun checkState(state: String) {
        if (state == MultisigsState.signed.name) {
            contentView.error_btn.visibility = GONE
            showErrorInfo(getString(R.string.multisig_state_signed))
        } else if (state == MultisigsState.unlocked.name) {
            contentView.error_btn.visibility = GONE
            showErrorInfo(getString(R.string.multisig_state_unlocked))
        }
    }

    private fun showUserList(userList: ArrayList<User>, isSender: Boolean) {
        val title = getString(if (isSender) R.string.multisig_senders else R.string.multisig_receivers)
        UserListBottomSheetDialogFragment.newInstance(userList, title)
            .showNow(parentFragmentManager, UserListBottomSheetDialogFragment.TAG)
    }

    override fun getBiometricItem() = t

    override suspend fun invokeNetwork(pin: String): MixinResponse<Void> {
        return when {
            t.action == MultisigsAction.sign.name -> {
                bottomViewModel.signMultisigs(t.requestId, pin)
            }
            else -> {
                bottomViewModel.unlockMultisigs(t.requestId, pin)
            }
        }
    }

    override fun doWhenInvokeNetworkSuccess() {
        success = true
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)
        if (!success &&
            t.state != MultisigsState.signed.name &&
            t.state != MultisigsState.unlocked.name) {
            GlobalScope.launch(Dispatchers.IO) {
                bottomViewModel.cancelMultisigs(t.requestId)
            }
        }
    }
}