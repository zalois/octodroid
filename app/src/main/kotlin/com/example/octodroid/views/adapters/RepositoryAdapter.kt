package com.example.octodroid.views.adapters

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import com.example.octodroid.data.GitHub
import com.example.octodroid.data.prefs.OctodroidPrefs
import com.example.octodroid.views.components.DividerItemDecoration
import com.example.octodroid.views.components.LinearLayoutLoadMoreListener
import com.example.octodroid.views.helpers.ToastHelper
import com.example.octodroid.views.holders.ProgressViewHolder
import com.example.octodroid.views.holders.SelectableRepositoryItemViewHolder
import com.jakewharton.rxbinding.view.RxView
import com.rejasupotaro.octodroid.http.Params
import com.rejasupotaro.octodroid.http.Response
import com.rejasupotaro.octodroid.models.Repository
import com.rejasupotaro.octodroid.models.Resource
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.*

class RepositoryAdapter(private val recyclerView: RecyclerView) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private object ViewType {
        val ITEM = 1
        val FOOTER = 2
    }

    private val context: Context
    private val repositories = ArrayList<Repository>()
    private var responseSubject: BehaviorSubject<Observable<Response<List<Repository>>>>? = null
    private var pagedResponse: Observable<Response<List<Repository>>>? = null
    private var isReachedLast: Boolean = false

    private val prefs: OctodroidPrefs
    private val selectedRepositories = HashMap<Int, Repository>()

    init {
        this.context = recyclerView.context

        prefs = OctodroidPrefs.get(context)
        for (serializedRepositories in prefs.selectedSerializedRepositories) {
            val repository = Resource.fromJson(serializedRepositories, Repository::class.java)
            selectedRepositories.put(repository.id, repository)
        }

        val layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(false)
        recyclerView.itemAnimator = null
        recyclerView.addItemDecoration(DividerItemDecoration(context))
        recyclerView.addOnScrollListener(object : LinearLayoutLoadMoreListener(layoutManager) {
            override fun onLoadMore() {
                if (pagedResponse != null) {
                    responseSubject!!.onNext(pagedResponse)
                }
            }
        })

        requestUserRepositories()
    }

    private fun requestUserRepositories() {
        val params = Params()
        params.add("per_page", "100")
        params.add("sort", "updated")
        responseSubject = BehaviorSubject.create(GitHub.client().userRepositories(params))
        responseSubject!!.takeUntil(RxView.detaches(recyclerView))
                .flatMap { r -> r }
                .subscribe({ r ->
                    if (!r.isSuccessful) {
                        isReachedLast = true
                        notifyDataSetChanged()
                        ToastHelper.showError(context)
                        return@subscribe
                    }

                    val items = r.entity()
                    val startPosition = repositories.size
                    repositories.addAll(items)

                    if (r.hasNext()) {
                        pagedResponse = r.next()
                    } else {
                        isReachedLast = true
                    }

                    if (startPosition == 0) {
                        notifyDataSetChanged()
                    } else {
                        notifyItemRangeInserted(startPosition, items.size)
                    }
                })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == ViewType.FOOTER) {
            return ProgressViewHolder.create(parent)
        } else {
            return SelectableRepositoryItemViewHolder.create(parent)
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            ViewType.FOOTER -> {
            }
            else -> {
                val repository = repositories[position]
                (viewHolder as SelectableRepositoryItemViewHolder).bind(
                        repository,
                        selectedRepositories.containsKey(repository.id),
                        object : SelectableRepositoryItemViewHolder.OnSelectRepositoryListener {
                            override fun onSelect(id: Int) {
                                selectedRepositories.put(id, repository)
                            }

                            override fun onUnSelect(id: Int) {
                                selectedRepositories.remove(id)
                            }
                        })
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (repositories.size == 0 || position == repositories.size) {
            return ViewType.FOOTER
        } else {
            return ViewType.ITEM
        }
    }

    override fun getItemCount(): Int {
        return repositories.size + if (isReachedLast) 0 else 1
    }

    fun saveSelectedRepositories() {
        val selectedSerializedRepositories = HashSet<String>()
        for (repositoryId in selectedRepositories.keys) {
            val repository = selectedRepositories[repositoryId]
            selectedSerializedRepositories.add(repository!!.toJson())
        }
        prefs.selectedSerializedRepositories = selectedSerializedRepositories
    }
}

