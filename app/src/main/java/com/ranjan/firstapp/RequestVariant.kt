package com.ranjan.firstapp

import org.json.JSONObject

data class RequestVariant(val data: JSONObject,
                          val query: Map<String, String>,
                          val headers: Map<String, String>)
