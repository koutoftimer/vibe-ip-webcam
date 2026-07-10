package ua.pp.ruslan_kovtun.ipwebcam

/** A captured JPEG frame: raw bytes plus the number of valid bytes in the buffer. */
class Frame(val bytes: ByteArray, val length: Int)
