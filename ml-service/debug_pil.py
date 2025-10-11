import sys, importlib, traceback, pkgutil
print('sys.path[0:5]=', sys.path[:5])
try:
    import PIL
    print('PIL repr:', PIL)
    print('PIL __file__:', getattr(PIL, '__file__', None))
    print('PIL __spec__:', getattr(PIL, '__spec__', None))
    print('PIL __path__:', getattr(PIL, '__path__', None))
    try:
        import PIL.Image as PImg
        print('PIL.Image file:', getattr(PImg, '__file__', None))
        print('PIL.Image type:', type(PImg))
    except Exception:
        print('Failed importing PIL.Image')
        traceback.print_exc()
except Exception:
    print('Failed importing PIL')
    traceback.print_exc()
print('pkgutil.find_loader("PIL"):', pkgutil.find_loader('PIL'))
print('pkgutil.get_loader("PIL"):', pkgutil.get_loader('PIL'))
print('End diagnostic')
