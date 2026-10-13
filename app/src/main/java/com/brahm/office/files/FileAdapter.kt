package com.brahm.office.files

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.brahm.office.R
import com.brahm.office.util.DocType
import com.brahm.office.util.FileItem
import com.brahm.office.util.FileRepo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mixed list: String = section header (folder name), FileItem = file row. */
class FileAdapter(
    private var items: List<Any>,
    private val onClick: (FileItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val T_HEADER = 0
    private val T_FILE = 1

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) { val title: TextView = v.findViewById(R.id.headerTitle) }
    class FileVH(v: View) : RecyclerView.ViewHolder(v) {
        val badge: TextView = v.findViewById(R.id.badge)
        val name: TextView = v.findViewById(R.id.name)
        val meta: TextView = v.findViewById(R.id.meta)
    }

    fun submit(list: List<Any>) { items = list; notifyDataSetChanged() }

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = if (items[position] is String) T_HEADER else T_FILE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == T_HEADER) HeaderVH(inf.inflate(R.layout.item_header, parent, false))
        else FileVH(inf.inflate(R.layout.item_file, parent, false))
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        val item = items[pos]
        if (h is HeaderVH && item is String) { h.title.text = item; return }
        if (h is FileVH && item is FileItem) {
            h.name.text = item.name
            h.meta.text = "${FileRepo.humanSize(item.size)}  ·  ${fmt.format(Date(item.modified))}"
            h.badge.text = when (item.type) {
                DocType.PDF -> "PDF"; DocType.WORD -> "DOC"; DocType.EXCEL -> "XLS"
                DocType.CSV -> "CSV"; DocType.PPT -> "PPT"; DocType.TEXT -> "TXT"; else -> "FILE"
            }
            h.badge.setBackgroundColor(colorFor(item.type))
            h.itemView.setOnClickListener { onClick(item) }
        }
    }

    private fun colorFor(t: DocType): Int = when (t) {
        DocType.PDF -> Color.parseColor("#D64541")
        DocType.EXCEL, DocType.CSV -> Color.parseColor("#1F9D63")
        DocType.WORD -> Color.parseColor("#2B579A")
        DocType.PPT -> Color.parseColor("#D24726")
        else -> Color.parseColor("#5B6472")
    }
}
