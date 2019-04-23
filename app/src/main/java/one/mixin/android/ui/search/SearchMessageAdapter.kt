package one.mixin.android.ui.search

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.TextViewCompat
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_search_message.view.*
import one.mixin.android.R
import one.mixin.android.extension.highLight
import one.mixin.android.extension.timeAgo
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.SearchMessageDetailItem
import org.jetbrains.anko.dip

class SearchMessageAdapter : PagedListAdapter<SearchMessageDetailItem, SearchMessageHolder>(SearchMessageDetailItem.DIFF_CALLBACK) {
    var query: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        SearchMessageHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_message, parent, false))

    override fun onBindViewHolder(holder: SearchMessageHolder, position: Int) {
        getItem(position)?.let {
            holder.bind(it, query, callback)
        }
    }

    var callback: SearchMessageCallback? = null

    interface SearchMessageCallback {
        fun onItemClick(item: SearchMessageDetailItem)
    }
}

class SearchMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val icon: Drawable? by lazy {
        AppCompatResources.getDrawable(itemView.context, R.drawable.ic_status_file).apply {
            this?.setBounds(0, 0, itemView.dip(12f), itemView.dip(12f))
        }
    }

    fun bind(
        message: SearchMessageDetailItem,
        query: String,
        searchMessageCallback: SearchMessageAdapter.SearchMessageCallback?
    ) {
        itemView.search_name_tv.text = message.userFullName
        if (message.type == MessageCategory.SIGNAL_DATA.name || message.type == MessageCategory.PLAIN_DATA.name) {
            TextViewCompat.setCompoundDrawablesRelative(itemView.search_msg_tv, icon, null, null, null)
            itemView.search_msg_tv.text = message.mediaName
        } else {
            TextViewCompat.setCompoundDrawablesRelative(itemView.search_msg_tv, null, null, null, null)
            itemView.search_msg_tv.text = message.content
        }
        message.content?.let {
            itemView.search_msg_tv.text = beautifulContent(it, query)
        }
        itemView.search_time_tv.timeAgo(message.createdAt)
        itemView.search_msg_tv.highLight(query)
        itemView.search_avatar_iv.setInfo(message.userFullName, message.userAvatarUrl, message.userId)
        itemView.setOnClickListener {
            searchMessageCallback?.onItemClick(message)
        }
    }

    private fun beautifulContent(content: String, query: String): String {
        val index = content.indexOf(query)
        if (index == -1) return content

        val arr = content.split(query)
        val cutIndex = if (arr[0].length > 10) {
            index - 10
        } else {
            index
        }
        val preContent = content.substring(cutIndex)
        return preContent.replace('\n', ' ')
    }
}
