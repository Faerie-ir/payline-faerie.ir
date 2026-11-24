'B4A Class Module: FaeriePayline.bas
'اتصال مستقیم برنامه‌های B4A (Basic4Android) به درگاه پرداخت فائری (Faerie Payline)
'نیازمندی‌ها در B4A IDE: کتابخانه‌های داخلی OkHttpUtils2 ، JSON و Phone

Sub Class_Globals
	Private mBaseUrl As String
	Private mToken As String
	Private mCallback As Object
	Private mEventName As String
	Public LastPurchaseId As String
End Sub

Public Sub Initialize(CallbackModule As Object, EventName As String, BaseUrl As String, PaylineToken As String)
	mCallback = CallbackModule
	mEventName = EventName
	mBaseUrl = BaseUrl
	If mBaseUrl.EndsWith("/") Then mBaseUrl = mBaseUrl.SubString2(0, mBaseUrl.Length - 1)
	mToken = PaylineToken
End Sub

'۱) دریافت فهرست آیتم‌های خرید برنامه
Public Sub FetchProducts
	Dim j As HttpJob
	j.Initialize("products", Me)
	j.Download(mBaseUrl & "/api/v1/iap/products")
	j.GetRequest.SetHeader("X-Payline", mToken)
End Sub

'۲) شروع تراکنش و باز کردن خودکار برنامه/درگاه فائری
Public Sub StartPurchase(Sku As String, UserId As String, Mobile As String, NationalCode As String, DiscountCode As String)
	Dim m As Map
	m.Initialize
	m.Put("sku", Sku)
	m.Put("user_id", UserId)
	If Mobile <> "" Then m.Put("mobile", Mobile)
	If NationalCode <> "" Then m.Put("national_code", NationalCode)
	If DiscountCode <> "" Then m.Put("discount_code", DiscountCode)

	Dim gen As JSONGenerator
	gen.Initialize(m)

	Dim j As HttpJob
	j.Initialize("init", Me)
	j.PostString(mBaseUrl & "/api/v1/iap/init", gen.ToString)
	j.GetRequest.SetContentType("application/json")
	j.GetRequest.SetHeader("X-Payline", mToken)
End Sub

'۳) بررسی وضعیت تراکنش در Activity_Resume
Public Sub VerifyPurchase(PurchaseId As String)
	Dim j As HttpJob
	j.Initialize("status", Me)
	j.Download(mBaseUrl & "/api/v1/iap/status/" & PurchaseId)
	j.GetRequest.SetHeader("X-Payline", mToken)
End Sub

'۴) ثبت مصرف آیتم مصرفی
Public Sub ConsumePurchase(PurchaseId As String, UserId As String)
	Dim m As Map
	m.Initialize
	m.Put("purchase_id", PurchaseId)
	m.Put("user_id", UserId)
	Dim gen As JSONGenerator
	gen.Initialize(m)

	Dim j As HttpJob
	j.Initialize("consume", Me)
	j.PostString(mBaseUrl & "/api/v1/iap/consume", gen.ToString)
	j.GetRequest.SetContentType("application/json")
	j.GetRequest.SetHeader("X-Payline", mToken)
End Sub

Private Sub JobDone(Job As HttpJob)
	If Job.Success Then
		Dim parser As JSONParser
		parser.Initialize(Job.GetString)
		Dim root As Map = parser.NextObject
		Dim ok As Boolean = root.GetDefault("success", False)
		If ok Then
			Dim data As Map = root.Get("data")
			Select Job.JobName
				Case "init"
					Dim p As Map = data.Get("purchase")
					LastPurchaseId = p.Get("purchase_id")
					Dim payUrl As String = data.Get("payment_url")
					OpenFaerieOrBrowser(LastPurchaseId, payUrl)
				Case "status"
					Dim st As String = data.Get("status")
					If st = "paid" Or st = "consumed" Then
						CallSubDelayed2(mCallback, mEventName & "_PurchaseSucceeded", data)
					Else
						CallSubDelayed2(mCallback, mEventName & "_PurchaseFailed", "وضعیت تراکنش: " & st)
					End If
				Case "products"
					CallSubDelayed2(mCallback, mEventName & "_ProductsLoaded", data.Get("products"))
				Case "consume"
					CallSubDelayed2(mCallback, mEventName & "_Consumed", data)
			End Select
		Else
			CallSubDelayed2(mCallback, mEventName & "_PurchaseFailed", root.GetDefault("message", "خطای درگاه"))
		End If
	Else
		CallSubDelayed2(mCallback, mEventName & "_PurchaseFailed", Job.ErrorMessage)
	End If
	Job.Release
End Sub

Private Sub OpenFaerieOrBrowser(PurchaseId As String, PaymentUrl As String)
	Dim pi As PhoneIntents
	Try
		StartActivity(pi.OpenBrowser("faerie://pay/" & PurchaseId))
	Catch
		StartActivity(pi.OpenBrowser(PaymentUrl))
	End Try
End Sub
