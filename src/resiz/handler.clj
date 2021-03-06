(ns resiz.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [image-resizer.resize :refer :all]
            [image-resizer.format :as format]
            [image-resizer.scale-methods :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]))

(defn get-full-path [path]
  (-> path (io/resource) (.getPath)))

(defn compute-image-file-ratio [path]
  (with-open [stream (java.io.FileInputStream. (get-full-path path))]
    (let [image (javax.imageio.ImageIO/read stream)]

      (/ (.getWidth image) (.getHeight image)))))

(defn ratio-valid? [path w h]
  (let [width (read-string w)
        height (read-string h)]

    (= (/ width height) (compute-image-file-ratio path))))

(defn number-string? [number-string]
  (integer? (read-string number-string)))

(defn dimensions-valid? [& dimensions]
  (every? number-string? dimensions))

(defn image-exists? [path]
  (not (nil? (io/resource path))))

(defn valid? [path w h]
  (and (dimensions-valid? w h) (ratio-valid? path w h)))

(defn resize-image [path width height]
  (let [resizer (resize-fn width height speed)
        img-file (-> path (get-full-path) (io/file))]

        (format/as-stream (resizer img-file) "jpg")))

(defn error-response [status message]
  (response/status (response/response message) status))

(defn resize-handler [path w h]
  (cond
   (not (image-exists? path)) (error-response 404 "image does not exists")
   (not (valid? path w h)) (error-response 400 "invalid parameters")
   :else (let [width   (Integer. w)
            height (Integer. h)]

        (with-open [image-stream (resize-image path width height)]
          (-> image-stream
              (response/response)
              (response/content-type "image/jpeg"))))))

(defroutes resize-image-routes
   (GET "/:width/:height/*" request
    (let [params (request :params)
          width (params :width)
          height (params :height)
          image-path (params :*)]

          (resize-handler image-path width height)))
   (route/not-found "Not Found"))

(def app
  (wrap-defaults resize-image-routes site-defaults))
