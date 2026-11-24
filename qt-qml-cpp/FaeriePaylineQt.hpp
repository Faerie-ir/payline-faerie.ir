#pragma once
#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QNetworkReply>
#include <QJsonDocument>
#include <QJsonObject>
#include <QDesktopServices>
#include <QUrl>

/**
 * FaeriePaylineQt — هدر تک‌فایلی C++ / Qt6 برای اتصال برنامه‌های Qt/QML روی اندروید و دسکتاپ به فائری.
 */
class FaeriePaylineQt : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString baseUrl READ baseUrl WRITE setBaseUrl NOTIFY configChanged)
    Q_PROPERTY(QString paylineToken READ paylineToken WRITE setPaylineToken NOTIFY configChanged)
public:
    explicit FaeriePaylineQt(QObject* parent = nullptr) : QObject(parent) {}

    QString baseUrl() const { return m_baseUrl; }
    void setBaseUrl(const QString& u) { m_baseUrl = u; emit configChanged(); }

    QString paylineToken() const { return m_token; }
    void setPaylineToken(const QString& t) { m_token = t; emit configChanged(); }

    Q_INVOKABLE void startPurchase(const QString& sku, const QString& userId) {
        QJsonObject body{{"sku", sku}, {"user_id", userId}};
        QNetworkRequest req(QUrl(m_baseUrl + "/api/v1/iap/init"));
        req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
        req.setRawHeader("X-Payline", m_token.toUtf8());
        auto* reply = m_nam.post(req, QJsonDocument(body).toJson());
        connect(reply, &QNetworkReply::finished, this, [this, reply]() {
            reply->deleteLater();
            auto root = QJsonDocument::fromJson(reply->readAll()).object();
            if (!root["success"].toBool()) {
                emit purchaseFailed(root["message"].toString("خطا در شروع پرداخت"));
                return;
            }
            auto data = root["data"].toObject();
            QString payUrl = data["payment_url"].toString();
            m_lastPurchaseId = data["purchase"].toObject()["purchase_id"].toString();
            QDesktopServices::openUrl(QUrl(payUrl));
        });
    }

    Q_INVOKABLE void verifyLastPurchase(const QString& userId) {
        if (m_lastPurchaseId.isEmpty()) return;
        QString pid = m_lastPurchaseId;
        m_lastPurchaseId.clear();
        QNetworkRequest req(QUrl(m_baseUrl + "/api/v1/iap/status/" + pid));
        req.setRawHeader("X-Payline", m_token.toUtf8());
        auto* reply = m_nam.get(req);
        connect(reply, &QNetworkReply::finished, this, [this, reply, pid, userId]() {
            reply->deleteLater();
            auto root = QJsonDocument::fromJson(reply->readAll()).object();
            auto d = root["data"].toObject();
            QString st = d["status"].toString();
            if (st == "paid" || st == "consumed") {
                emit purchaseSucceeded(pid, d["invoice_number"].toString());
            } else {
                emit purchaseFailed("وضعیت تراکنش: " + st);
            }
        });
    }

signals:
    void configChanged();
    void purchaseSucceeded(const QString& purchaseId, const QString& invoiceNumber);
    void purchaseFailed(const QString& message);

private:
    QString m_baseUrl = "https://faerie.ir";
    QString m_token;
    QString m_lastPurchaseId;
    QNetworkAccessManager m_nam;
};
