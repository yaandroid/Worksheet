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

class FileAdapter(
    private var items: List<FileItem>,
    private val onClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val badge: TextView = v.findViewById(R.id.badge)
        val name: TextView = v.findViewById(R.id.name)
        val meta: TextView = v.findViewById(R.id.meta)
    }

    fun submit(list: List<FileItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val f = items[pos]
        h.name.text = f.name
        h.meta.text = "${FileRepo.humanSize(f.size)}  ·  ${fmt.format(Date(f.modified))}"
        h.badge.text = when (f.type) {
            DocType.PDF -> "PDF"; DocType.WORD -> "DOC"; DocType.EXCEL -> "XLS"
            DocType.CSV -> "CSV"; DocType.PPT -> "PPT"; DocType.TEXT -> "TXT"
            else -> "FILE"
        }
        h.badge.setBackgroundColor(colorFor(f.type))
        h.itemView.setOnClickListener { onClick(f) }
    }

    private fun colorFor(t: DocType): Int = when (t) {
        DocType.PDF -> Color.parseColor("#D64541")
        DocType.EXCEL, DocType.CSV -> Color.parseColor("#1F9D63")
        DocType.WORD -> Color.parseColor("#2B579A")
        DocType.PPT -> Color.parseColor("#D24726")
        else -> Color.parseColor("#5B6472")
    }
}
