package com.github.username.cardapp.ui.collection

import java.io.IOException

class ImportFileTooLargeException(maxBytes: Int) : IOException("File exceeds $maxBytes bytes")
