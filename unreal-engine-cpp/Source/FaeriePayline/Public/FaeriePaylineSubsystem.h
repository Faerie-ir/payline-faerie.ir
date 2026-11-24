#pragma once

#include "CoreMinimal.h"
#include "Subsystems/GameInstanceSubsystem.h"
#include "Interfaces/IHttpRequest.h"
#include "FaeriePaylineSubsystem.generated.h"

DECLARE_DYNAMIC_MULTICAST_DELEGATE_ThreeParams(FFaeriePurchaseSuccess, const FString&, PurchaseId, const FString&, Sku, const FString&, InvoiceNumber);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FFaeriePurchaseFailure, const FString&, ErrorMessage);

/**
 * UFaeriePaylineSubsystem — زیرسیستم Blueprint و C++ درگاه فائری برای Unreal Engine 5.
 */
UCLASS(BlueprintType)
class UFaeriePaylineSubsystem : public UGameInstanceSubsystem
{
    GENERATED_BODY()

public:
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Faerie Payline")
    FString BaseUrl = TEXT("https://faerie.ir");

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "Faerie Payline")
    FString PaylineToken = TEXT("pl_YOUR_PAYLINE_TOKEN");

    UPROPERTY(BlueprintAssignable, Category = "Faerie Payline")
    FFaeriePurchaseSuccess OnPurchaseSucceeded;

    UPROPERTY(BlueprintAssignable, Category = "Faerie Payline")
    FFaeriePurchaseFailure OnPurchaseFailed;

    UFUNCTION(BlueprintCallable, Category = "Faerie Payline")
    void StartPurchase(const FString& Sku, const FString& UserId, const FString& Mobile = TEXT(""), const FString& DiscountCode = TEXT(""));

    UFUNCTION(BlueprintCallable, Category = "Faerie Payline")
    void VerifyAndConsume(const FString& PurchaseId, const FString& UserId, bool bAutoConsume = true);
};
