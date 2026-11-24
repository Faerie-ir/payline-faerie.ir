package payline

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type Client struct {
	BaseURL      string
	PaylineToken string
	HTTPClient   *http.Client
}

type InitParams struct {
	SKU          string `json:"sku"`
	UserID       string `json:"user_id"`
	Amount       int    `json:"amount,omitempty"`
	Mobile       string `json:"mobile,omitempty"`
	NationalCode string `json:"national_code,omitempty"`
	DiscountCode string `json:"discount_code,omitempty"`
	ReturnURL    string `json:"return_url,omitempty"`
}

type Purchase struct {
	PurchaseID    string `json:"purchase_id"`
	SKU           string `json:"sku"`
	AmountToman   int    `json:"amount_toman"`
	GrossToman    int    `json:"gross_toman"`
	DiscountToman int    `json:"discount_toman"`
	Status        string `json:"status"`
	RefNumber     string `json:"ref_number"`
	PaymentURL    string `json:"payment_url"`
	InvoiceNumber string `json:"invoice_number"`
}

func (p Purchase) IsPaid() bool {
	return p.Status == "paid" || p.Status == "consumed"
}

func NewClient(token string) *Client {
	return &Client{
		BaseURL:      "https://faerie.ir",
		PaylineToken: strings.TrimSpace(token),
		HTTPClient:   &http.Client{Timeout: 20 * time.Second},
	}
}

func (c *Client) InitPurchase(ctx context.Context, p InitParams) (*Purchase, error) {
	var env struct {
		Success bool   `json:"success"`
		Message string `json:"message"`
		Data    struct {
			Purchase   Purchase `json:"purchase"`
			PaymentURL string   `json:"payment_url"`
		} `json:"data"`
	}
	if err := c.do(ctx, http.MethodPost, "/api/v1/iap/init", p, &env); err != nil {
		return nil, err
	}
	out := env.Data.Purchase
	if out.PaymentURL == "" {
		out.PaymentURL = env.Data.PaymentURL
	}
	return &out, nil
}

func (c *Client) GetStatus(ctx context.Context, purchaseID string) (*Purchase, error) {
	var env struct {
		Success bool     `json:"success"`
		Message string   `json:"message"`
		Data    Purchase `json:"data"`
	}
	if err := c.do(ctx, http.MethodGet, "/api/v1/iap/status/"+url.PathEscape(purchaseID), nil, &env); err != nil {
		return nil, err
	}
	return &env.Data, nil
}

func (c *Client) Consume(ctx context.Context, purchaseID, userID string) (*Purchase, error) {
	var env struct {
		Success bool     `json:"success"`
		Message string   `json:"message"`
		Data    Purchase `json:"data"`
	}
	body := map[string]string{"purchase_id": purchaseID, "user_id": userID}
	if err := c.do(ctx, http.MethodPost, "/api/v1/iap/consume", body, &env); err != nil {
		return nil, err
	}
	return &env.Data, nil
}

func (c *Client) do(ctx context.Context, method, path string, body any, out any) error {
	var rdr io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return err
		}
		rdr = bytes.NewReader(b)
	}
	req, err := http.NewRequestWithContext(ctx, method, strings.TrimRight(c.BaseURL, "/")+path, rdr)
	if err != nil {
		return err
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("X-Payline", c.PaylineToken)
	if body != nil {
		req.Header.Set("Content-Type", "application/json; charset=utf-8")
	}
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)

	var base struct {
		Success bool   `json:"success"`
		Message string `json:"message"`
	}
	_ = json.Unmarshal(raw, &base)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 || !base.Success {
		if base.Message != "" {
			return fmt.Errorf("faerie payline (%d): %s", resp.StatusCode, base.Message)
		}
		return fmt.Errorf("faerie payline http %d", resp.StatusCode)
	}
	return json.Unmarshal(raw, out)
}
