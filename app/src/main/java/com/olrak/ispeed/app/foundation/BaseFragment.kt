package com.olrak.ispeed.app.foundation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.olrak.ispeed.app.util.SpeedTestHandler
import com.olrak.ispeed.app.util.ViewUtil
import javax.inject.Inject

abstract class BaseFragment<VB: ViewBinding>(
    private val bindingInflater: (inflater: LayoutInflater) -> VB
) : Fragment() {

    @Inject
    lateinit var speedTestHandler: SpeedTestHandler

    @Inject
    lateinit var viewUtil: ViewUtil

    private var _binding: VB? = null

    protected val binding get() = _binding!!

    protected open fun onCreated(savedInstanceState: Bundle?) = Unit

    protected open fun onViewCreated() = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onCreated(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewCreated()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = initViewBinding(inflater)

    private fun initViewBinding(
        inflater: LayoutInflater
    ): View {
        _binding = bindingInflater.invoke(inflater)
        return binding.root
    }

    fun getAppActivity(): AppCompatActivity = (activity as AppCompatActivity)

    fun onBackPressedCallBack(onBackClicked: () -> Unit) =
    getAppActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
        onBackPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBackClicked.invoke()
        }
    )

    override fun onDestroyView() {
        super.onDestroyView()
        // Set the viewBinding into "null" to avoid memory leaks,
        // because Fragments outlives their view,
        // after onDestroyView being called.
        _binding = null
    }
}