package com.thesis.ispeed.dashboard


import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.thesis.ispeed.R
import com.thesis.ispeed.app.foundation.BaseFragment
import com.thesis.ispeed.app.shared.extension.showFancyToast
import com.thesis.ispeed.app.shared.widget.DialogFactory
import com.thesis.ispeed.app.shared.widget.DialogFactory.Companion.showCustomInfoDialog
import com.thesis.ispeed.databinding.FragmentDashboardBinding
import com.thesis.ispeed.login.presentation.LoginActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DashboardFragment : BaseFragment<FragmentDashboardBinding>(bindingInflater = FragmentDashboardBinding::inflate) {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onViewCreated() {
        super.onViewCreated()
        with(binding) {
            setupComponents()
            setupObserver()
        }
    }

    private fun setupObserver() {
        with(viewModel) {
            userDetails.observe(viewLifecycleOwner) {
                it?.let {
                    binding.toolBar.txtUserToolbar.text = "${it.firstName} ${it.lastName}"
                }
            }
        }
    }

    private fun FragmentDashboardBinding.setupComponents() {
        toolBar.txtLogout.setOnClickListener {
            showFancyToast(message = "Successfully Logged out.")
            viewModel.logoutUser()
            navigationUtil.navigateActivity(getAppActivity(), LoginActivity::class.java, willFinish = true)
        }

        cvSpeedTest.setOnClickListener {
            findNavController().navigate(directions = DashboardFragmentDirections.navigateToSpeedTestFragment())
        }

        cvStab.setOnClickListener {
            findNavController().navigate(directions = DashboardFragmentDirections.navigateToAutomaticTrackFragment())
        }

        cvGeomap.setOnClickListener {
            findNavController().navigate(directions = DashboardFragmentDirections.navigateToGeoMapFragment())
        }

        instructionLayout.setOnClickListener {
            showCustomInfoDialog(
                context = requireContext(),
                dialogAttributes = DialogFactory.InformationDialogAttributes(
                    title = "INSTRUCTIONS",
                    header = "How to use this service?",
                    firstLineTitle = requireContext().getString(R.string.instructions_manual_tracking),
                    secondLineTitle = requireContext().getString(R.string.instructions_automatic_tracking),
                    thirdLineTitle = requireContext().getString(R.string.instructions_geo_map_tracking)
                )
            )
        }

        aboutLayout.setOnClickListener {
            showCustomInfoDialog(
                context = requireContext(),
                dialogAttributes = DialogFactory.InformationDialogAttributes(
                    title = "ABOUT",
                    header = "Purpose of each Testing Services",
                    firstLineTitle = "This service will manually track your internet connectivity capturing its ping, download, and upload speed.",
                    secondLineTitle = "This service will provide real-time and compiled reports of the user’s internet stability status. The graph will show the unstable/stable reports gathered during testing that are sectioned through timely synchronization (Days, Weeks, Months). The table below shows the continuous report of the user’s internet stability status.",
                    thirdLineTitle = "This service will provide reports regarding the behavior of user’s internet connectivity in their current location."
                )
            )
        }
    }
}