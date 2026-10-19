package com.example.sudokumaster

import android.app.Application
import com.example.sudokumaster.viewmodel.AppContainer

class SudokuApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
