# frozen_string_literal: true

require 'json'
require 'net/http'
require 'uri'

# Faerie Payline SDK for Ruby & Ruby on Rails (Zero dependencies)
module Faerie
  class PaylineError < StandardError; end

  class PaylineClient
    def initialize(payline_token, base_url: 'https://faerie.ir')
      @token = payline_token.strip
      @base_url = base_url.chomp('/')
    end

    def products
      request(:get, '/api/v1/iap/products').dig('data', 'products') || []
    end

    def init_purchase(sku:, user_id:, **extras)
      res = request(:post, '/api/v1/iap/init', { sku: sku, user_id: user_id }.merge(extras))
      data = res['data'] || {}
      tx = data['purchase'] || data
      tx['payment_url'] ||= data['payment_url']
      tx
    end

    def status(purchase_id)
      request(:get, "/api/v1/iap/status/#{URI.encode_www_form_component(purchase_id)}")['data']
    end

    def consume(purchase_id, user_id)
      request(:post, '/api/v1/iap/consume', { purchase_id: purchase_id, user_id: user_id })['data']
    end

    private

    def request(method, path, body = nil)
      uri = URI.parse(@base_url + path)
      req = method == :get ? Net::HTTP::Get.new(uri) : Net::HTTP::Post.new(uri)
      req['Accept'] = 'application/json'
      req['X-Payline'] = @token
      if body
        req['Content-Type'] = 'application/json; charset=utf-8'
        req.body = JSON.generate(body)
      end
      res = Net::HTTP.start(uri.hostname, uri.port, use_ssl: uri.scheme == 'https') { |http| http.request(req) }
      parsed = JSON.parse(res.body)
      raise PaylineError, (parsed['message'] || "HTTP #{res.code}") unless res.is_a?(Net::HTTPSuccess) && parsed['success']

      parsed
    end
  end
end
