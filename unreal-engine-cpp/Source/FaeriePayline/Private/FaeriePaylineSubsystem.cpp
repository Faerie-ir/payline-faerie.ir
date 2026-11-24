#include "FaeriePaylineSubsystem.h"
#include "HttpModule.h"
#include "Interfaces/IHttpResponse.h"
#include "Dom/JsonObject.h"
#include "Serialization/JsonReader.h"
#include "Serialization/JsonSerializer.h"
#include "Kismet/KismetSystemLibrary.h"

void UFaeriePaylineSubsystem::StartPurchase(const FString& Sku, const FString& UserId, const FString& Mobile, const FString& DiscountCode)
{
    TSharedPtr<FJsonObject> Body = MakeShareable(new FJsonObject());
    Body->SetStringField(TEXT("sku"), Sku);
    Body->SetStringField(TEXT("user_id"), UserId);
    if (!Mobile.IsEmpty()) Body->SetStringField(TEXT("mobile"), Mobile);
    if (!DiscountCode.IsEmpty()) Body->SetStringField(TEXT("discount_code"), DiscountCode);

    FString Payload;
    TSharedRef<TJsonWriter<>> Writer = TJsonWriterFactory<>::Create(&Payload);
    FJsonSerializer::Serialize(Body.ToSharedRef(), Writer);

    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> Req = FHttpModule::Get().CreateRequest();
    Req->SetURL(BaseUrl.TrimChar('/') + TEXT("/api/v1/iap/init"));
    Req->SetVerb(TEXT("POST"));
    Req->SetHeader(TEXT("Content-Type"), TEXT("application/json; charset=utf-8"));
    Req->SetHeader(TEXT("X-Payline"), PaylineToken);
    Req->SetContentAsString(Payload);

    Req->OnProcessRequestComplete().BindLambda([this](FHttpRequestPtr, FHttpResponsePtr Resp, bool bOk)
    {
        if (!bOk || !Resp.IsValid())
        {
            OnPurchaseFailed.Broadcast(TEXT("خطا در اتصال به درگاه فائری"));
            return;
        }
        TSharedPtr<FJsonObject> Root;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Resp->GetContentAsString());
        if (!FJsonSerializer::Deserialize(Reader, Root) || !Root->GetBoolField(TEXT("success")))
        {
            OnPurchaseFailed.Broadcast(Root.IsValid() ? Root->GetStringField(TEXT("message")) : TEXT("خطای سرور"));
            return;
        }
        const TSharedPtr<FJsonObject>* DataObj;
        if (Root->TryGetObjectField(TEXT("data"), DataObj))
        {
            FString PayUrl = (*DataObj)->GetStringField(TEXT("payment_url"));
            FPlatformProcess::LaunchURL(*PayUrl, nullptr, nullptr);
        }
    });
    Req->ProcessRequest();
}

void UFaeriePaylineSubsystem::VerifyAndConsume(const FString& PurchaseId, const FString& UserId, bool bAutoConsume)
{
    TSharedRef<IHttpRequest, ESPMode::ThreadSafe> Req = FHttpModule::Get().CreateRequest();
    Req->SetURL(BaseUrl.TrimChar('/') + TEXT("/api/v1/iap/status/") + PurchaseId);
    Req->SetVerb(TEXT("GET"));
    Req->SetHeader(TEXT("X-Payline"), PaylineToken);

    Req->OnProcessRequestComplete().BindLambda([this, PurchaseId, UserId, bAutoConsume](FHttpRequestPtr, FHttpResponsePtr Resp, bool bOk)
    {
        if (!bOk || !Resp.IsValid())
        {
            OnPurchaseFailed.Broadcast(TEXT("عدم ارتباط با سرور فائری"));
            return;
        }
        TSharedPtr<FJsonObject> Root;
        TSharedRef<TJsonReader<>> Reader = TJsonReaderFactory<>::Create(Resp->GetContentAsString());
        if (!FJsonSerializer::Deserialize(Reader, Root) || !Root->GetBoolField(TEXT("success")))
        {
            OnPurchaseFailed.Broadcast(TEXT("تأیید تراکنش ناموفق بود"));
            return;
        }
        TSharedPtr<FJsonObject> Data = Root->GetObjectField(TEXT("data"));
        FString Status = Data->GetStringField(TEXT("status"));
        if (Status != TEXT("paid") && Status != TEXT("consumed"))
        {
            OnPurchaseFailed.Broadcast(TEXT("وضعیت تراکنش: ") + Status);
            return;
        }
        FString Sku = Data->GetStringField(TEXT("sku"));
        FString Inv = Data->GetStringField(TEXT("invoice_number"));

        if (bAutoConsume && Status == TEXT("paid"))
        {
            TSharedRef<IHttpRequest, ESPMode::ThreadSafe> CReq = FHttpModule::Get().CreateRequest();
            CReq->SetURL(BaseUrl.TrimChar('/') + TEXT("/api/v1/iap/consume"));
            CReq->SetVerb(TEXT("POST"));
            CReq->SetHeader(TEXT("Content-Type"), TEXT("application/json"));
            CReq->SetHeader(TEXT("X-Payline"), PaylineToken);
            CReq->SetContentAsString(FString::Printf(TEXT("{\"purchase_id\":\"%s\",\"user_id\":\"%s\"}"), *PurchaseId, *UserId));
            CReq->ProcessRequest();
        }
        OnPurchaseSucceeded.Broadcast(PurchaseId, Sku, Inv);
    });
    Req->ProcessRequest();
}
