(ns flight.util.captcha
  (:require [flight.util.session :as session])
  (:import net.sf.jlue.util.Captcha
           javax.imageio.ImageIO
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (org.apache.commons.codec.binary Base64)))

(defn- bytes-to-base64 [bytes]
  (.toString (Base64/encodeBase64String bytes)))

(defn- gen-captcha-text []
    (->> #(rand-int 26) (repeatedly 6) (map (partial + 97)) (map char) (apply str)))

(defn gen []
  (let [text (gen-captcha-text)
        captcha (doto (new Captcha))]
    (session/flash-put! :captcha {:text text})
    (with-open [out (new ByteArrayOutputStream)]
      (ImageIO/write (.gen captcha text 218 26) "jpeg" out)
      (.flush out)
      (bytes-to-base64 (.toByteArray out)))))
