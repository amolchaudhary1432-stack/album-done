"""First Kaggle job: is a GPU there, and which one? (No photos.)"""
import json, subprocess, torch
info = {"cuda": torch.cuda.is_available(), "torch": torch.__version__}
if info["cuda"]:
    info["gpus"] = [torch.cuda.get_device_name(i) for i in range(torch.cuda.device_count())]
    info["memory_gb"] = round(torch.cuda.get_device_properties(0).total_memory / 1e9, 1)
    x = torch.randn(4096, 4096, device="cuda"); torch.cuda.synchronize()
    import time; t = time.time(); [x @ x for _ in range(50)]; torch.cuda.synchronize()
    info["tflops"] = round(50 * 2 * 4096 ** 3 / (time.time() - t) / 1e12, 1)
print(json.dumps(info))
json.dump(info, open("gpu.json", "w"))
print(subprocess.run(["nvidia-smi"], capture_output=True, text=True).stdout)
