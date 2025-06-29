package com.moshy.drugcalc.dbtest

import java.nio.file.Files

internal val tmpDir by lazy {
    Files.createTempDirectory("drugcalc-db-").toString()
}
