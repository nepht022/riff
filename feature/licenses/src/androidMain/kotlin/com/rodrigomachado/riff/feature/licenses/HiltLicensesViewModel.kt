package com.rodrigomachado.riff.feature.licenses

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** O [LicensesViewModel] criado pelo Hilt no celular. */
@HiltViewModel
class HiltLicensesViewModel @Inject constructor(source: LicenseSource) : LicensesViewModel(source)
