"""SmolVLM2-500M (Apache 2.0) through plain ONNX Runtime, as the phone would run it: one image tile of
512 px = 64 tokens, typed answers read from the next-token scores (no text generation needed)."""
import numpy as np, onnxruntime as ort, time
from tokenizers import Tokenizer
from PIL import Image
D = '/tmp/claude-0/-home-user-album-done/24045610-cfbe-58ce-8e80-2faf86d4e1cf/scratchpad/vlm/smol500'
tok = Tokenizer.from_file(D + '/tokenizer.json')
so = ort.SessionOptions(); so.intra_op_num_threads = 4
vis = ort.InferenceSession(D + '/onnx/vision_encoder_q4.onnx', so); emb = ort.InferenceSession(D + '/onnx/embed_tokens_int8.onnx', so)
dec = ort.InferenceSession(D + '/onnx/decoder_model_merged_q4.onnx', so)
IMG = tok.token_to_id('<image>'); EOU = tok.token_to_id('<end_of_utterance>')
past_names = [i.name for i in dec.get_inputs() if i.name.startswith('past_key_values')]
ONE = '<fake_token_around_image><global-img>' + '<image>' * 64 + '<fake_token_around_image>'
def pixels(im):
    """Longest side 512, padded to a 512 square (mask marks the real pixels), values -1..1."""
    im = im.convert('RGB'); w, h = im.size; s = 512 / max(w, h); im = im.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    a = np.zeros((3, 512, 512), np.float32); m = np.zeros((512, 512), bool)
    x = (np.asarray(im, np.float32) / 255 - .5) / .5; a[:, :im.size[1], :im.size[0]] = x.transpose(2, 0, 1); m[:im.size[1], :im.size[0]] = True
    return a, m
def prepare(images, question):
    text = '<|im_start|>User:' + ''.join(ONE for _ in images) + question + '<end_of_utterance>\nAssistant:'
    ids = np.array([tok.encode(text, add_special_tokens=False).ids], np.int64)
    pm = [pixels(i) for i in images]
    feats = vis.run(None, {'pixel_values': np.stack([p for p, _ in pm])[None], 'pixel_attention_mask': np.stack([m for _, m in pm])[None]})[0]
    e = emb.run(None, {'input_ids': ids})[0]
    e[0, ids[0] == IMG] = feats.reshape(-1, feats.shape[-1])
    return e
def step(e, past=None, n_past=0):
    feed = {'inputs_embeds': e, 'attention_mask': np.ones((1, n_past + e.shape[1]), np.int64), 'position_ids': np.arange(n_past + 1, n_past + e.shape[1] + 1, dtype=np.int64)[None]}
    for n in past_names: feed[n] = past[n] if past else np.zeros((1, 5, 0, 64), np.float32)
    out = dec.run(None, feed)
    return out[0], {n: o for n, o in zip(past_names, out[1:])}
def choose(images, question, options):
    """Scores of each option's first token as the next word."""
    e = prepare(images, question); logits, _ = step(e)
    ids = [tok.encode(' ' + o, add_special_tokens=False).ids[0] for o in options]
    l = logits[0, -1, ids]; p = np.exp(l - l.max()); return p / p.sum()
def say(images, question, n=24):
    e = prepare(images, question); logits, past = step(e); k = e.shape[1]; out = []
    for _ in range(n):
        t = int(logits[0, -1].argmax())
        if t == EOU: break
        out.append(t); logits, past = step(emb.run(None, {'input_ids': np.array([[t]])})[0], past, k); k += 1
    return tok.decode(out)
