$n = 0

While($n -lt 100)
{
If (!(Get-Process -Name cmd -ErrorAction SilentlyContinue))
{
    & 'C:/Users/35477/OneDrive/Desktop/HR/Onn_5/velraent_gagnanam/Project 2/Threadneedle-dev/build' --cl --b=configs/dl_stocksim.batch
    $n++
} else {
    Write-Output "Sleeping..."
    Start-Sleep -s 1
    Write-Output "Wakey Wakey, eggs and bakey"
}

}