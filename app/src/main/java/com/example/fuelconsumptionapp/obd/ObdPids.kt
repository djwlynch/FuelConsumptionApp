package com.example.fuelconsumptionapp.obd

object ObdPids {
    // Service 01 (show current data)
    const val SPEED = "010D" // A: km/h
    const val RPM = "010C"   // A,B: ((A*256)+B)/4 rpm
    const val MAP = "010B"   // A: kPa
    const val IAT = "010F"   // A: °C = A - 40
    const val MAF = "0110"   // A,B: (256*A + B) / 100 g/s
    const val STFT_B1 = "0106" // A: (A-128)*100/128 %
    const val LTFT_B1 = "0107" // A: (A-128)*100/128 %

    // Equivalence ratio (phi). Convert to lambda via lambda = 1/phi.
    // 0144 = Commanded equivalence ratio (phi): (A*256+B)/32768
    const val COMMANDED_EQ_RATIO = "0144"

    // 0134 = O2S1 equivalence ratio + voltage (wideband on many cars)
    // Equivalence ratio (phi): (A*256+B)/32768 ; voltage: (C*256+D)/8192
    const val O2S1_EQ_RATIO_VOLT = "0134"
}

