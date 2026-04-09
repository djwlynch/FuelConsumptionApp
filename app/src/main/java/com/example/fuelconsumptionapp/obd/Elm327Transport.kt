package com.example.fuelconsumptionapp.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.util.UUID

class Elm327Transport(
    private val device: BluetoothDevice,
) : Closeable {

    // Standard SerialPortService ID (SPP). Most classic ELM327 use this.
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    @SuppressLint("MissingPermission")
    suspend fun connect() = withContext(Dispatchers.IO) {
        if (socket != null) return@withContext
        val s = device.createRfcommSocketToServiceRecord(sppUuid)
        s.connect()
        socket = s
        input = BufferedInputStream(s.inputStream)
        output = BufferedOutputStream(s.outputStream)
    }

    suspend fun initializeElm(): List<String> = withContext(Dispatchers.IO) {
        val cmds = listOf(
            "ATZ",   // reset
            "ATE0",  // echo off
            "ATL0",  // linefeeds off
            "ATS0",  // spaces off
            "ATH0",  // headers off
            "ATSP0", // auto protocol
        )
        cmds.map { sendCommand(it) }
    }

    suspend fun sendCommand(cmd: String, timeoutMs: Long = 1200L): String =
        withContext(Dispatchers.IO) {
            val out = output ?: throw IOException("Not connected")
            val inp = input ?: throw IOException("Not connected")

            out.write((cmd.trim() + "\r").toByteArray(Charsets.US_ASCII))
            out.flush()

            // Read until '>' prompt
            val sb = StringBuilder()
            val start = System.currentTimeMillis()
            while (true) {
                if (System.currentTimeMillis() - start > timeoutMs) break
                val b = inp.read()
                if (b == -1) break
                val c = b.toChar()
                if (c == '>') break
                sb.append(c)
            }

            // Normalize: remove prompt leftovers, whitespace lines
            sb.toString()
                .replace("\r", "\n")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != cmd }
                .joinToString("\n")
        }

    override fun close() {
        try {
            input?.close()
        } catch (_: Throwable) {
        }
        try {
            output?.close()
        } catch (_: Throwable) {
        }
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        input = null
        output = null
        socket = null
    }
}

