package com.franzkafkayu.vcserver.mosh

class MoshException(val code: String, cause: Throwable? = null) : Exception(code, cause)
