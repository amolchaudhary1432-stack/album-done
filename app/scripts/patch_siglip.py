"""The phone's ONNX Runtime (Android) has no ConvInteger for 8-bit photos x 8-bit weights, which the int8
SigLIP 2 model uses once (its patch embedding). This writes the same model with that one layer as plain
float maths: Conv over (photo - zero point) and (weights - zero point), whose result equals ConvInteger's
whole numbers (the next layer turns them into floats anyway). Everything else is untouched.
    python3 -I patch_siglip.py IN.onnx OUT.onnx"""
import sys
import numpy as np, onnx
from onnx import helper, numpy_helper, TensorProto
m = onnx.load(sys.argv[1])
g = m.graph
inits = {i.name: i for i in g.initializer}
new_nodes = []
for n in g.node:
    if n.op_type != 'ConvInteger':
        new_nodes.append(n); continue
    x, w, xz, wz = n.input
    wf = numpy_helper.to_array(inits[w]).astype(np.float32) - numpy_helper.to_array(inits[wz]).astype(np.float32)
    wname = w + '_as_float'
    g.initializer.append(numpy_helper.from_array(wf, wname))
    new_nodes += [
        helper.make_node('Cast', [x], [x + '_f'], to=TensorProto.FLOAT, name=n.name + '_cast_x'),
        helper.make_node('Cast', [xz], [xz + '_f'], to=TensorProto.FLOAT, name=n.name + '_cast_xz'),
        helper.make_node('Sub', [x + '_f', xz + '_f'], [x + '_centred'], name=n.name + '_centre_x'),
        helper.make_node('Conv', [x + '_centred', wname], list(n.output), name=n.name + '_float', **{a.name: helper.get_attribute_value(a) for a in n.attribute}),
    ]
    print('replaced', n.name)
del g.node[:]
g.node.extend(new_nodes)
used = {i for nd in g.node for i in nd.input}
keep = [i for i in g.initializer if i.name in used]
del g.initializer[:]
g.initializer.extend(keep)
onnx.checker.check_model(m)
onnx.save(m, sys.argv[2])
