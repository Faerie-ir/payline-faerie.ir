use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Clone)]
pub struct FaeriePayline {
    base_url: String,
    token: String,
    http: Client,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Purchase {
    pub purchase_id: String,
    pub sku: String,
    pub amount_toman: i64,
    pub status: String,
    pub payment_url: Option<String>,
    pub invoice_number: Option<String>,
}

impl Purchase {
    pub fn is_paid(&self) -> bool {
        self.status == "paid" || self.status == "consumed"
    }
}

#[derive(Debug, Serialize)]
pub struct InitRequest<'a> {
    pub sku: &'a str,
    pub user_id: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub mobile: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub national_code: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub return_url: Option<&'a str>,
}

#[derive(Deserialize)]
struct ApiEnvelope<T> {
    success: bool,
    message: Option<String>,
    data: Option<T>,
}

#[derive(Deserialize)]
struct InitData {
    purchase: Purchase,
    payment_url: Option<String>,
}

impl FaeriePayline {
    pub fn new(payline_token: impl Into<String>) -> Self {
        Self {
            base_url: "https://faerie.ir".to_string(),
            token: payline_token.into(),
            http: Client::new(),
        }
    }

    pub async fn init_purchase(&self, req: &InitRequest<'_>) -> Result<Purchase, String> {
        let env: ApiEnvelope<InitData> = self
            .http
            .post(format!("{}/api/v1/iap/init", self.base_url.trim_end_matches('/')))
            .header("X-Payline", &self.token)
            .json(req)
            .send()
            .await
            .map_err(|e| e.to_string())?
            .json()
            .await
            .map_err(|e| e.to_string())?;

        if !env.success {
            return Err(env.message.unwrap_or_else(|| "خطای درگاه فائری".into()));
        }
        let mut d = env.data.ok_or("پاسخ خالی از سرور")?;
        if d.purchase.payment_url.is_none() {
            d.purchase.payment_url = d.payment_url;
        }
        Ok(d.purchase)
    }

    pub async fn get_status(&self, purchase_id: &str) -> Result<Purchase, String> {
        let env: ApiEnvelope<Purchase> = self
            .http
            .get(format!("{}/api/v1/iap/status/{}", self.base_url.trim_end_matches('/'), purchase_id))
            .header("X-Payline", &self.token)
            .send()
            .await
            .map_err(|e| e.to_string())?
            .json()
            .await
            .map_err(|e| e.to_string())?;

        if !env.success {
            return Err(env.message.unwrap_or_else(|| "خطای استعلام وضعیت".into()));
        }
        env.data.ok_or_else(|| "پاسخ نامعتبر".into())
    }
}
