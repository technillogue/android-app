package one.mixin.android.ui.conversation.holder

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.SeekBar
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.TextViewCompat
import com.google.android.exoplayer2.util.MimeTypes
import com.google.gson.Gson
import kotlinx.android.synthetic.main.item_chat_file_quote.view.*
import kotlinx.android.synthetic.main.layout_file_holder_bottom.view.*
import one.mixin.android.R
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.fileSize
import one.mixin.android.extension.formatMillis
import one.mixin.android.extension.loadImageCenterCrop
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.extension.round
import one.mixin.android.extension.timeAgoClock
import one.mixin.android.ui.conversation.adapter.ConversationAdapter
import one.mixin.android.util.AudioPlayer
import one.mixin.android.vo.MediaStatus
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.QuoteMessageItem
import org.jetbrains.anko.dip
import org.jetbrains.anko.textResource

class FileQuoteHolder constructor(containerView: View) : MediaHolder(containerView) {
    private val dp16 = itemView.context.dpToPx(16f)
    private val dp8 = itemView.context.dpToPx(8f)

    init {
        val radius = itemView.context.dpToPx(4f).toFloat()
        itemView.reply_layout.round(radius)
    }

    override fun chatLayout(isMe: Boolean, isLast: Boolean, isBlink: Boolean) {
        super.chatLayout(isMe, isLast, isBlink)
        if (isMe) {
            itemView.chat_msg_layout.gravity = Gravity.END
            if (isLast) {
                setItemBackgroundResource(
                        itemView.chat_layout,
                        R.drawable.chat_bubble_reply_me_last,
                        R.drawable.chat_bubble_reply_me_last_night
                )
            } else {
                setItemBackgroundResource(
                        itemView.chat_layout,
                        R.drawable.chat_bubble_reply_me,
                        R.drawable.chat_bubble_reply_me_night
                )
            }
        } else {
            itemView.chat_msg_layout.gravity = Gravity.START

            if (isLast) {
                setItemBackgroundResource(
                        itemView.chat_layout,
                        R.drawable.chat_bubble_reply_other_last,
                        R.drawable.chat_bubble_reply_other_last_night
                )
            } else {
                setItemBackgroundResource(
                        itemView.chat_layout,
                        R.drawable.chat_bubble_reply_other,
                        R.drawable.chat_bubble_reply_other_night
                )
            }
        }
    }

    private var onItemListener: ConversationAdapter.OnItemListener? = null

    fun bind(
            messageItem: MessageItem,
            keyword: String?,
            isFirst: Boolean,
            isLast: Boolean,
            hasSelect: Boolean,
            isSelect: Boolean,
            onItemListener: ConversationAdapter.OnItemListener
    ) {
        if (hasSelect && isSelect) {
            itemView.setBackgroundColor(SELECT_COLOR)
        } else {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        val isMe = meId == messageItem.userId
        chatLayout(isMe, isLast)
        if (isFirst && !isMe) {
            itemView.chat_name.visibility = View.VISIBLE
            itemView.chat_name.text = messageItem.userFullName
            if (messageItem.appId != null) {
                itemView.chat_name.setCompoundDrawables(null, null, botIcon, null)
                itemView.chat_name.compoundDrawablePadding = itemView.dip(3)
            } else {
                itemView.chat_name.setCompoundDrawables(null, null, null, null)
            }
            itemView.chat_name.setTextColor(getColorById(messageItem.userId))
            itemView.chat_name.setOnClickListener { onItemListener.onUserClick(messageItem.userId) }
        } else {
            itemView.chat_name.visibility = View.GONE
        }
        itemView.chat_time.timeAgoClock(messageItem.createdAt)
        keyword.notNullWithElse({ k ->
            messageItem.mediaName?.let { str ->
                val start = str.indexOf(k, 0, true)
                if (start >= 0) {
                    val sp = SpannableString(str)
                    sp.setSpan(BackgroundColorSpan(HIGHLIGHTED), start,
                            start + k.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    itemView.file_name_tv.text = sp
                } else {
                    itemView.file_name_tv.text = messageItem.mediaName
                }
            }
        }, {
            itemView.file_name_tv.text = messageItem.mediaName
        })
        if (messageItem.mediaStatus == MediaStatus.EXPIRED.name) {
            itemView.file_size_tv.textResource = R.string.chat_expired
        } else {
            itemView.file_size_tv.text = "${messageItem.mediaSize?.fileSize()}"
        }

        itemView.seek_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (MimeTypes.isAudio(messageItem.mediaMimeType) &&
                        AudioPlayer.get().isPlay(messageItem.messageId)) {
                    AudioPlayer.get().seekTo(seekBar.progress)
                }
            }
        })
        messageItem.mediaStatus?.let {
            when (it) {
                MediaStatus.EXPIRED.name -> {
                    itemView.file_expired.visibility = View.VISIBLE
                    itemView.file_progress.visibility = View.INVISIBLE
                    itemView.chat_layout.setOnClickListener {
                        handleClick(hasSelect, isSelect, isMe, messageItem, onItemListener)
                    }
                }
                MediaStatus.PENDING.name -> {
                    itemView.file_expired.visibility = View.GONE
                    itemView.file_progress.visibility = View.VISIBLE
                    itemView.file_progress.enableLoading()
                    itemView.file_progress.setBindId(messageItem.messageId)
                    itemView.file_progress.setOnClickListener {
                        onItemListener.onCancel(messageItem.messageId)
                    }
                    itemView.chat_layout.setOnClickListener {
                        handleClick(hasSelect, isSelect, isMe, messageItem, onItemListener)
                    }
                }
                MediaStatus.DONE.name, MediaStatus.READ.name -> {
                    itemView.file_expired.visibility = View.GONE
                    itemView.file_progress.visibility = View.VISIBLE
                    if (MimeTypes.isAudio(messageItem.mediaMimeType)) {
                        itemView.file_progress.setBindOnly(messageItem.messageId)
                        itemView.bottom_layout.bindId = messageItem.messageId
                        if (AudioPlayer.get().isPlay(messageItem.messageId)) {
                            itemView.file_progress.setPause()
                            itemView.bottom_layout.showSeekBar()
                        } else {
                            itemView.file_progress.setPlay()
                            itemView.bottom_layout.showText()
                        }
                        itemView.file_progress.setOnClickListener {
                            onItemListener.onAudioFileClick(messageItem)
                        }
                    } else {
                        itemView.file_progress.setDone()
                        itemView.file_progress.setBindId(null)
                        itemView.bottom_layout.bindId = null
                        itemView.file_progress.setOnClickListener {
                            handleClick(hasSelect, isSelect, isMe, messageItem, onItemListener)
                        }
                    }
                    itemView.chat_layout.setOnClickListener {
                        if (AudioPlayer.get().isPlay(messageItem.messageId)) {
                            onItemListener.onAudioFileClick(messageItem)
                        } else {
                            handleClick(hasSelect, isSelect, isMe, messageItem, onItemListener)
                        }
                    }
                }
                MediaStatus.CANCELED.name -> {
                    itemView.file_expired.visibility = View.GONE
                    itemView.file_progress.visibility = View.VISIBLE
                    if (isMe && messageItem.mediaUrl != null) {
                        itemView.file_progress.enableUpload()
                    } else {
                        itemView.file_progress.enableDownload()
                    }
                    itemView.file_progress.setBindId(messageItem.messageId)
                    itemView.file_progress.setProgress(-1)
                    itemView.file_progress.setOnClickListener {
                        if (isMe && messageItem.mediaUrl != null) {
                            onItemListener.onRetryUpload(messageItem.messageId)
                        } else {
                            onItemListener.onRetryDownload(messageItem.messageId)
                        }
                    }
                    itemView.chat_layout.setOnClickListener {
                        handleClick(hasSelect, isSelect, isMe, messageItem, onItemListener)
                    }
                }
            }
        }
        itemView.setOnClickListener {
            if (hasSelect) {
                onItemListener.onSelect(!isSelect, messageItem, adapterPosition)
            }
        }
        itemView.chat_layout.setOnLongClickListener {
            if (!hasSelect) {
                onItemListener.onLongClick(messageItem, adapterPosition)
            } else {
                onItemListener.onSelect(!isSelect, messageItem, adapterPosition)
                true
            }
        }
        itemView.setOnLongClickListener {
            if (!hasSelect) {
                onItemListener.onLongClick(messageItem, adapterPosition)
            } else {
                onItemListener.onSelect(!isSelect, messageItem, adapterPosition)
                true
            }
        }
        val quoteMessage = Gson().fromJson(messageItem.quoteContent, QuoteMessageItem::class.java)
        itemView.reply_name_tv.text = quoteMessage.userFullName
        itemView.reply_name_tv.setTextColor(getColorById(quoteMessage.userId))
        itemView.reply_layout.setBackgroundColor(getColorById(quoteMessage.userId))
        itemView.reply_layout.background.alpha = 0x0D
        itemView.start_view.setBackgroundColor(getColorById(quoteMessage.userId))
        when {
            quoteMessage.type.endsWith("_TEXT") -> {
                itemView.reply_content_tv.text = quoteMessage.content
                itemView.reply_iv.visibility = View.GONE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
                setIcon()
            }
            quoteMessage.type == MessageCategory.MESSAGE_RECALL.name -> {
                itemView.reply_content_tv.setText(R.string.chat_recall_me)
                itemView.reply_iv.visibility = View.GONE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
                setIcon(R.drawable.ic_status_recall)
            }
            quoteMessage.type.endsWith("_IMAGE") -> {
                itemView.reply_iv.loadImageCenterCrop(
                    quoteMessage.mediaUrl,
                    R.drawable.image_holder
                )
                itemView.reply_content_tv.setText(R.string.photo)
                setIcon(R.drawable.ic_status_pic)
                itemView.reply_iv.visibility = View.VISIBLE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
            }
            quoteMessage.type.endsWith("_VIDEO") -> {
                itemView.reply_iv.loadImageCenterCrop(
                    quoteMessage.mediaUrl,
                    R.drawable.image_holder
                )
                itemView.reply_content_tv.setText(R.string.video)
                setIcon(R.drawable.ic_status_video)
                itemView.reply_iv.visibility = View.VISIBLE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
            }
            quoteMessage.type.endsWith("_LIVE") -> {
                itemView.reply_iv.loadImageCenterCrop(
                    quoteMessage.thumbUrl,
                    R.drawable.image_holder
                )
                itemView.reply_content_tv.setText(R.string.live)
                setIcon(R.drawable.ic_status_live)
                itemView.reply_iv.visibility = View.VISIBLE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
            }
            quoteMessage.type.endsWith("_DATA") -> {
                quoteMessage.mediaName.notNullWithElse({
                    itemView.reply_content_tv.text = it
                }, {
                    itemView.reply_content_tv.setText(R.string.document)
                })
                setIcon(R.drawable.ic_status_file)
                itemView.reply_iv.visibility = View.GONE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
            }
            quoteMessage.type.endsWith("_POST") -> {
                itemView.reply_content_tv.setText(R.string.post)
                setIcon(R.drawable.ic_status_file)
                itemView.reply_iv.visibility = View.GONE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
            }
            quoteMessage.type.endsWith("_AUDIO") -> {
                quoteMessage.mediaDuration.notNullWithElse({
                    itemView.reply_content_tv.text = it.toLong().formatMillis()
                }, {
                    itemView.reply_content_tv.setText(R.string.audio)
                })
                setIcon(R.drawable.ic_status_audio)
                itemView.reply_iv.visibility = View.GONE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
            }
            quoteMessage.type.endsWith("_STICKER") -> {
                itemView.reply_content_tv.setText(R.string.conversation_status_sticker)
                setIcon(R.drawable.ic_status_stiker)
                itemView.reply_iv.loadImageCenterCrop(
                    quoteMessage.assetUrl,
                    R.drawable.image_holder
                )
                itemView.reply_iv.visibility = View.VISIBLE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
            }
            quoteMessage.type.endsWith("_CONTACT") -> {
                itemView.reply_content_tv.text = quoteMessage.sharedUserIdentityNumber
                setIcon(R.drawable.ic_status_contact)
                itemView.reply_avatar.setInfo(
                    quoteMessage.sharedUserFullName,
                    quoteMessage.sharedUserAvatarUrl,
                    quoteMessage.sharedUserId
                        ?: "0"
                )
                itemView.reply_avatar.visibility = View.VISIBLE
                itemView.reply_iv.visibility = View.INVISIBLE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp16
            }
            quoteMessage.type == MessageCategory.APP_BUTTON_GROUP.name || quoteMessage.type == MessageCategory.APP_CARD.name -> {
                itemView.reply_content_tv.setText(R.string.extensions)
                setIcon(R.drawable.ic_touch_app)
                itemView.reply_iv.visibility = View.GONE
                itemView.reply_avatar.visibility = View.GONE
                (itemView.reply_content_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
                (itemView.reply_name_tv.layoutParams as ConstraintLayout.LayoutParams).marginEnd =
                    dp8
            }
            else -> {
                itemView.reply_iv.visibility = View.GONE
            }
        }
    }
    private fun setIcon(@DrawableRes icon: Int? = null) {
        icon.notNullWithElse({ drawable ->
            AppCompatResources.getDrawable(itemView.context, drawable).let {
                it?.setBounds(0, 0, itemView.context.dpToPx(10f), itemView.context.dpToPx(10f))
                TextViewCompat.setCompoundDrawablesRelative(
                    itemView.reply_content_tv,
                    it,
                    null,
                    null,
                    null
                )
            }
        }, {
            TextViewCompat.setCompoundDrawablesRelative(
                itemView.reply_content_tv,
                null,
                null,
                null,
                null
            )
        })
    }
    private fun handleClick(
            hasSelect: Boolean,
            isSelect: Boolean,
            isMe: Boolean,
            messageItem: MessageItem,
            onItemListener: ConversationAdapter.OnItemListener
    ) {
        if (hasSelect) {
            onItemListener.onSelect(!isSelect, messageItem, adapterPosition)
        } else if (messageItem.mediaStatus == MediaStatus.CANCELED.name) {
            if (isMe) {
                onItemListener.onRetryUpload(messageItem.messageId)
            } else {
                onItemListener.onRetryDownload(messageItem.messageId)
            }
        } else if (messageItem.mediaStatus == MediaStatus.PENDING.name) {
            onItemListener.onCancel(messageItem.messageId)
        } else if (messageItem.mediaStatus == MediaStatus.EXPIRED.name) {
        } else {
            onItemListener.onFileClick(messageItem)
        }
    }
}
