package net.swigg.tradr.trade

import com.google.common.hash.Hashing
import org.apache.commons.codec.digest.Crypt
import org.apache.http.client.utils.URIBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Service
import org.springframework.web.client.*
import java.nio.charset.Charset
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class TradeService {
    fun accounts(): List<Account> {
        val path = "/accounts"
        val rest = RestTemplate()
        val method = HttpMethod.GET
        val endpoint = URIBuilder().setScheme("https")
          .setHost("api.gdax.com")
          .setPath(path)
          .build()

        rest.errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse?): Boolean {
                return response?.statusCode?.equals(HttpStatus.OK) ?: false
            }

            override fun handleError(response: ClientHttpResponse?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        }

        rest.execute(endpoint, method, RequestCallback { request ->
            val time = Instant.now()
            val dateFormatter = DateTimeFormatterBuilder()
              .appendPattern("yyyy-MM-dd")
              .appendLiteral("T")
              .appendPattern("hh:mm:ss.SSSSSSX")
              .toFormatter()

            request.headers.apply {
                set("CB-ACCESS-KEY", "38ca1c50904b7e03e3b49de5778a03a9")
                set("CB-ACCESS-PASSPHRASE", "bbol5g6726")
                set("CB-ACCESS-TIMESTAMP", dateFormatter.format(time.atOffset(ZoneOffset.UTC)))
                set("CB-ACCESS-SIGN", signHeader(dateFormatter.format(time.atOffset(ZoneOffset.UTC)), method.name, path))
            }
        }, ResponseExtractor<List<Account>> { response ->
            println(response)
            listOf()
        })

        return listOf()
    }

    private fun signHeader(timestamp: String, method: String, path: String, body: String? = null): String {
        var prehash = timestamp.plus(method).plus(path)
        body?.let {
            prehash = prehash.plus(body)
        }

        val secret = "o5h+u5l+4WfjYgBfqMA33QLKMMKnRJDf0Bi+R/frXv86D1hK7ScyiUIQuMlk2KtByZXu5LFOADpXZcnVgeJCnQ=="
        val mac = Mac.getInstance("HmacSHA256");
        mac.init(SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(prehash.toByteArray()))


//        return Base64.getEncoder().encodeToString(hasher.hashUnencodedChars(prehash).asBytes()) ?: ""
//        return Base64.getEncoder().encodeToString(hasher.hashString(prehash, Charset.defaultCharset()).asBytes())
    }
}

data class Account(
  var id: UUID?,
  var currency: Currency?,
  var balance: Double?,
  var available: Double?,
  var hold: Double?,
  var profileId: UUID?
)

enum class Currency {
    BTC,
    USD,
    LTC,
    ETH
}
