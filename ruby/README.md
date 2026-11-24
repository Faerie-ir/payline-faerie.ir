# Faerie Payline SDK — Ruby & Ruby on Rails

کلاینت سبک و بدون وابستگی **Ruby** برای سرویس‌های **Ruby on Rails** و Sinatra.

```ruby
payline = Faerie::PaylineClient.new(ENV.fetch('FAERIE_PAYLINE_TOKEN'))
tx = payline.init_purchase(sku: 'pro_plan', user_id: current_user.id.to_s, return_url: callback_url)
redirect_to tx['payment_url'], allow_other_host: true
```
